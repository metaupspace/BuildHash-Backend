package com.builddash.backend.api.dto.response;

import java.util.UUID;

public record ReorderResponse(
        UUID cartId,
        String message
) {
}
