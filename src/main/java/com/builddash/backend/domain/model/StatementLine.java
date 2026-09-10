package com.builddash.backend.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One accounting line of a statement period (9-E). Read keyset-paged from the accounting
 * repository and drained page by page by the workbook writer.
 */
public record StatementLine(UUID orderId, UUID siteId, UUID productId, String productName,
                            int quantity, BigDecimal unitPrice, BigDecimal taxAmount, BigDecimal lineTotal,
                            UUID lineId) {
}
