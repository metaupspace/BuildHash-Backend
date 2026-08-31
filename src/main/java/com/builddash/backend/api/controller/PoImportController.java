package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.response.PoImportConvertResponse;
import com.builddash.backend.api.dto.response.PoImportResponse;
import com.builddash.backend.application.service.PoConversionService;
import com.builddash.backend.application.service.PoImportService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Bulk PO import surface. companyId names the target company and is never
 * trusted — B2bAuthorizer resolves membership/permission against current DB
 * state inside the service. Convert is bodyless (site selection deferred).
 */
@RestController
@RequestMapping("/po")
@Tag(name = "PO bulk import", description = "Streaming XLSX import and draft conversion")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PoImportController {

    private final PoImportService poImportService;
    private final PoConversionService poConversionService;

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk-import PO rows from an XLSX workbook (PO_UPLOAD, Idempotency-Key required)")
    public ResponseEntity<PoImportResponse> importWorkbook(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("companyId") UUID companyId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthenticatedUser user) {
        PoImportService.ImportResult result = poImportService.importWorkbook(
                user.userId(), companyId, idempotencyKey, file);
        // Fresh parse -> 201; same company + key -> 200 with the original resource
        // (REVIEW or FAILED_STRUCTURE alike — locked decision 6).
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(PoImportResponse.from(result.poImport(), result.rows()));
    }

    @GetMapping("/imports/{importId}")
    @Operation(summary = "Get an import with its row outcomes (PO_VIEW)")
    public PoImportResponse get(@PathVariable UUID importId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        PoImportService.ImportDetail detail = poImportService.get(user.userId(), importId);
        return PoImportResponse.from(detail.poImport(), detail.rows());
    }

    @PostMapping("/imports/{importId}/convert")
    @Operation(summary = "Convert a REVIEW import into its B2B draft cart (PO_CONVERT)")
    public PoImportConvertResponse convert(@PathVariable UUID importId,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        UUID cartId = poConversionService.convert(user.userId(), importId);
        return new PoImportConvertResponse(importId, "CONVERTED", cartId);
    }
}
