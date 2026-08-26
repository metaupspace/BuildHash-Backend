package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Invoice;

import java.util.UUID;

public interface InvoiceCommitService {
    Invoice commit(UUID invoiceId, String storageKey);
}
