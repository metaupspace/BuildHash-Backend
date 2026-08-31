package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.RfqQuote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RfqQuoteRepository {

    RfqQuote save(RfqQuote quote);

    Optional<RfqQuote> findById(UUID id);

    Optional<RfqQuote> findByRfqIdAndVendorId(UUID rfqId, UUID vendorId);

    /** Comparison view: all quotes of an RFQ ordered by totalAmount ascending. */
    List<RfqQuote> findByRfqIdOrderByTotalAmountAsc(UUID rfqId);
}
