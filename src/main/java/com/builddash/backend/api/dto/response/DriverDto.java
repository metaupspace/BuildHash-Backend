package com.builddash.backend.api.dto.response;

public record DriverDto(
        String name,
        String maskedPhone,
        boolean callProxyAvailable
) {}
