package com.builddash.backend.application.service;

import java.util.UUID;

public interface InvoiceGenerationService {
    void processInvoice(UUID invoiceId);
}
