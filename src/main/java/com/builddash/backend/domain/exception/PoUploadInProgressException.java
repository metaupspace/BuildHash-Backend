package com.builddash.backend.domain.exception;

import java.util.UUID;

/**
 * The order already holds a PENDING claim from an earlier upload that never
 * finalized. A fresh upload must not silently overwrite it: the response names
 * the claim so the client can complete it explicitly via the retry endpoint.
 * 409 PO_UPLOAD_IN_PROGRESS.
 */
public class PoUploadInProgressException extends DomainException {

    public PoUploadInProgressException(UUID orderId, UUID attachmentId) {
        super("PO_UPLOAD_IN_PROGRESS",
                "A PO upload is already in progress for order " + orderId
                        + " (attachment " + attachmentId + "); retry it via "
                        + "POST /orders/" + orderId + "/po/" + attachmentId + "/retry");
    }
}
