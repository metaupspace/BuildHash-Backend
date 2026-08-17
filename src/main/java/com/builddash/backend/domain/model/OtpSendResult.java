package com.builddash.backend.domain.model;

public record OtpSendResult(long expiresInSeconds, boolean existingUser) {
}
