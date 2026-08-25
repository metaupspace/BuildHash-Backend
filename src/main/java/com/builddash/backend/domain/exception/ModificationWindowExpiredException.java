package com.builddash.backend.domain.exception;

public class ModificationWindowExpiredException extends DomainException {
    public ModificationWindowExpiredException() {
        super("MODIFICATION_WINDOW_EXPIRED", "Order modification window has expired");
    }
}
