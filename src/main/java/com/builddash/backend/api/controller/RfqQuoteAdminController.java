package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.AdminQuoteSubmitRequest;
import com.builddash.backend.application.service.RfqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Application-ADMIN controlled quote submission (ROLE_ADMIN via SecurityConfig).
 * Delegates to RfqService, which resolves AND locks the RFQ before changing
 * quote state — the controller never touches RFQ state itself.
 */
@RestController
@RequestMapping("/admin/rfqs")
@Tag(name = "RFQ quote administration", description = "Application-admin quote submission")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class RfqQuoteAdminController {

    private final RfqService rfqService;

    @PostMapping("/{rfqId}/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a vendor quote on an OPEN, routed RFQ")
    public void submitQuote(@PathVariable UUID rfqId,
                            @Valid @RequestBody AdminQuoteSubmitRequest request) {
        rfqService.submitQuote(rfqId, request.vendorId(), request.totalAmount(), request.validUntil());
    }
}
