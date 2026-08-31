package com.builddash.backend.api.dto.response;

import com.builddash.backend.application.service.RfqService;

import java.util.UUID;

/** Conversion outcome: the CONVERTED RFQ and the B2B_DRAFT cart it produced (checkout happens later, not here). */
public record RfqConvertResponse(
        UUID rfqId,
        String status,
        UUID cartId
) {

    public static RfqConvertResponse from(RfqService.ConversionResult result) {
        return new RfqConvertResponse(result.rfq().id(), result.rfq().status().name(), result.cartId());
    }
}
