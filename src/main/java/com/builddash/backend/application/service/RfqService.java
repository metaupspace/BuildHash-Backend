package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Rfq;
import com.builddash.backend.domain.model.RfqQuote;
import com.builddash.backend.domain.model.Vendor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Company-scoped RFQ operations. Authorization is always resolved by
 * B2bAuthorizer against current membership + permission state — the controller
 * supplies only the authenticated user, never a trusted company claim.
 * Quote submission is application-ADMIN territory (no B2B permission) but
 * still locks the RFQ here before changing quote state.
 */
public interface RfqService {

    Rfq create(UUID userId, UUID companyId, Instant expiresAt, String notes, List<ItemCommand> items);

    Rfq get(UUID userId, UUID rfqId);

    /** All quotes of an RFQ ordered by totalAmount ascending, with vendor info and the computed expired flag. */
    List<QuoteComparison> listQuotes(UUID userId, UUID rfqId);

    Rfq cancel(UUID userId, UUID rfqId);

    /** Converts the selected quote into a B2B_DRAFT cart; returns the updated RFQ and the new cart id. */
    ConversionResult convert(UUID userId, UUID rfqId, UUID quoteId);

    /** Controlled submission (application ADMIN): locks the RFQ, validates routing/validity, inserts. */
    RfqQuote submitQuote(UUID rfqId, UUID vendorId, BigDecimal totalAmount, Instant validUntil);

    record ItemCommand(UUID productId, int quantity) {
    }

    record QuoteComparison(RfqQuote quote, Vendor vendor, boolean expired) {
    }

    record ConversionResult(Rfq rfq, UUID cartId) {
    }
}
