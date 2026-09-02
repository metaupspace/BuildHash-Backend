package com.builddash.backend.domain.exception;

/**
 * The gateway returned a definitive rejection (not a timeout/network failure): the
 * external operation is known NOT to have taken effect. Safe to mark the local claim
 * FAILED and allow a retry — a new gateway call is not a double-charge/double-refund risk.
 */
public class GatewayRejectedException extends RuntimeException {

    public GatewayRejectedException(String message) {
        super(message);
    }

    public GatewayRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
