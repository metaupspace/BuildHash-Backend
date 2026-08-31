package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.PoAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * B2B PO document attachment to an existing order (PO_UPLOAD). Durable-claim
 * lifecycle: the fresh upload creates the PENDING claim and finalizes it; an
 * unfinished PENDING claim is only ever completed by the explicit retry that
 * names it — a new upload against the same order is rejected, never silently
 * overwritten.
 */
public interface PoAttachmentService {

    /** Fresh upload: claim -> store (outside tx) -> conditional finalize. 201. */
    PoAttachment upload(UUID userId, UUID orderId, MultipartFile file);

    /**
     * Explicit recovery of an existing PENDING claim: same attachment id, same
     * storage key. The conditional finalize decides the winner of concurrent
     * retries; the loser receives the already-finalized attachment.
     */
    RetryOutcome retry(UUID userId, UUID orderId, UUID attachmentId, MultipartFile file);

    record RetryOutcome(PoAttachment attachment, boolean finalizedNow) {
    }
}
