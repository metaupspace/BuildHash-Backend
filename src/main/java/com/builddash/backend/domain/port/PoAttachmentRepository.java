package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.PoAttachment;

import java.util.Optional;
import java.util.UUID;

public interface PoAttachmentRepository {

    PoAttachment save(PoAttachment attachment);

    Optional<PoAttachment> findById(UUID id);

    /** Row lock — serializes the claim-phase check of concurrent retries. */
    Optional<PoAttachment> findByIdForUpdate(UUID id);

    Optional<PoAttachment> findByOrderId(UUID orderId);

    /**
     * Conditional finalize PENDING -> STORED (single SQL UPDATE ... WHERE
     * status = 'PENDING'). Returns the finalized attachment, or empty when the
     * claim was already finalized by a concurrent retry — the loser re-reads.
     */
    Optional<PoAttachment> finalizeStored(UUID attachmentId, String contentType, int byteSize, UUID uploadedBy);
}
