package com.builddash.backend.domain.exception;

import lombok.Getter;

/**
 * Base for business-rule violations. Carries a stable machine-readable code and a message —
 * no HTTP status: that's an api/ concern, mapped in api/GlobalExceptionHandler, so domain
 * code stays ignorant of HTTP.
 */
@Getter
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }
}
