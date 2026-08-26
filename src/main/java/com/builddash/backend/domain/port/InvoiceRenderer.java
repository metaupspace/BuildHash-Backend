package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.OrderInvoiceSnapshot;

public interface InvoiceRenderer {
    byte[] render(OrderInvoiceSnapshot snapshot);
}
