package com.builddash.backend.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * deviceId is null when the validated token carries no device claim (guest tokens) —
 * callers never need to branch on token type to know whether it's safe to read.
 */
public record TokenClaims(UUID userId, UUID deviceId, List<String> roles) {
}
