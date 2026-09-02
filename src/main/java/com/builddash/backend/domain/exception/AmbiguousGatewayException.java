package com.builddash.backend.domain.exception;

/**
 * The gateway call failed in a way that does not tell us whether the external operation
 * took effect (timeout, connection drop, 5xx, unrecognized response). The external side
 * may have already succeeded — never treat this as a definitive rejection: the local
 * claim must stay non-terminal (PENDING) rather than FAILED, since FAILED is retry-eligible
 * and a retry here could issue a second real gateway operation.
 */
public class AmbiguousGatewayException extends RuntimeException {

    public AmbiguousGatewayException(String message) {
        super(message);
    }

    public AmbiguousGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
