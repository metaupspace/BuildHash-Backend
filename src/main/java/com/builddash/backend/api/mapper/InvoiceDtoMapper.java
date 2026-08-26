package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.InvoiceResponse;
import com.builddash.backend.domain.enums.InvoiceStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InvoiceDtoMapper {

    public InvoiceResponse toResponse(InvoiceStatus status, String invoiceNumber, String url, Instant expiresAt) {
        return new InvoiceResponse(status, invoiceNumber, url, expiresAt);
    }
}
