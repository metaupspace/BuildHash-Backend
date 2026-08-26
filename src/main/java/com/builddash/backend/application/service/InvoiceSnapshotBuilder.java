package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.OrderInvoiceSnapshot;

import java.util.UUID;

public interface InvoiceSnapshotBuilder {
    OrderInvoiceSnapshot build(UUID orderId);
}
