package com.builddash.backend.api.controller.order;

import com.builddash.backend.api.dto.response.InvoiceResponse;
import com.builddash.backend.api.mapper.InvoiceDtoMapper;
import com.builddash.backend.application.service.InvoiceQueryService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Invoices", description = "Tax invoice query and signed URL retrieval")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceQueryService invoiceQueryService;
    private final InvoiceDtoMapper invoiceDtoMapper;

    @GetMapping("/orders/{id}/invoice")
    @Operation(summary = "Get signed URL and status for an order invoice")
    public InvoiceResponse getInvoice(
            @PathVariable("id") UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        InvoiceQueryService.InvoiceQueryResult result = invoiceQueryService.getInvoice(user.userId(), orderId);
        return invoiceDtoMapper.toResponse(result.status(), result.invoiceNumber(), result.url(), result.expiresAt());
    }
}
