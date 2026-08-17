package com.builddash.backend.domain.model;

public record IssuedToken(String token, long expiresInSeconds) {
}
