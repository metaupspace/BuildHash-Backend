package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.response.PoAttachmentResponse;
import com.builddash.backend.application.service.PoAttachmentService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * B2B PO document attachment. Thin: authorization, claim lifecycle and storage
 * discipline all live in PoAttachmentServiceImpl. The retry endpoint is the
 * only recovery path for an unfinished PENDING claim — the upload endpoint
 * never overwrites one.
 */
@RestController
@Tag(name = "PO attachments", description = "Attach internal PO documentation to B2B orders")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PoAttachmentController {

    private final PoAttachmentService poAttachmentService;

    @PostMapping(value = "/orders/{orderId}/po", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach an XLSX PO document to a B2B order (PO_UPLOAD)")
    public PoAttachmentResponse upload(@PathVariable UUID orderId,
                                       @RequestParam("file") MultipartFile file,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        return PoAttachmentResponse.from(poAttachmentService.upload(user.userId(), orderId, file));
    }

    @PostMapping(value = "/orders/{orderId}/po/{attachmentId}/retry",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Explicitly retry an unfinished PENDING PO attachment claim (PO_UPLOAD)")
    public ResponseEntity<PoAttachmentResponse> retry(@PathVariable UUID orderId,
                                                      @PathVariable UUID attachmentId,
                                                      @RequestParam("file") MultipartFile file,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        PoAttachmentService.RetryOutcome outcome =
                poAttachmentService.retry(user.userId(), orderId, attachmentId, file);
        // Locked decision 2: a retry that finalizes is a 201; one that lost the
        // finalize race returns the existing attachment with 200.
        return ResponseEntity.status(outcome.finalizedNow() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PoAttachmentResponse.from(outcome.attachment()));
    }
}
