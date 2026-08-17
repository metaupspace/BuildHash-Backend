package com.builddash.backend.common;

import java.util.List;
import java.util.UUID;

public record AuthenticatedUser(UUID userId, UUID deviceId, List<String> roles) {
}
