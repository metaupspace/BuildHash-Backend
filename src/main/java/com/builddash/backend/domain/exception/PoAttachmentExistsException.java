package com.builddash.backend.domain.exception;

import java.util.UUID;

/** A STORED attachment already exists for the order — one PO document per order. 409. */
public class PoAttachmentExistsException extends DomainException {

    public PoAttachmentExistsException(UUID orderId, UUID attachmentId) {
        super("PO_ALREADY_EXISTS",
                "A PO attachment already exists for order " + orderId
                        + " (attachment " + attachmentId + ")");
    }
}
