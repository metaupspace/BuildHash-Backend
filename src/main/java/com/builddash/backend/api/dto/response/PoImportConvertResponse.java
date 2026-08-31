package com.builddash.backend.api.dto.response;

import java.util.UUID;

/** Conversion outcome: the CONVERTED import and its (single) B2B_DRAFT cart id. */
public record PoImportConvertResponse(
        UUID importId,
        String status,
        UUID draftCartId
) {
}
