package com.builddash.backend.domain.model;

public record AuthSession(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
}
