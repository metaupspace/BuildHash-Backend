package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Invoice;

import java.util.UUID;

public interface InvoiceClaimService {
    Invoice claim(UUID invoiceId);
}
