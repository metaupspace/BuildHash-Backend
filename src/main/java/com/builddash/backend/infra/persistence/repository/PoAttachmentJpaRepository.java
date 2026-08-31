package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.PoAttachmentStatus;
import com.builddash.backend.infra.persistence.entity.PoAttachmentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PoAttachmentJpaRepository extends JpaRepository<PoAttachmentEntity, UUID> {

    Optional<PoAttachmentEntity> findByOrderId(UUID orderId);

    /**
     * Claim serialization for the retry path: the winner of two concurrent
     * retries is decided by this conditional UPDATE, not by the store call.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PoAttachmentEntity a WHERE a.id = :id")
    Optional<PoAttachmentEntity> findByIdForUpdate(@Param("id") UUID id);

    /** Conditional finalize: 0 affected rows means a concurrent retry already STORED it. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PoAttachmentEntity a SET a.status = :stored, a.contentType = :contentType, "
            + "a.byteSize = :byteSize, a.uploadedBy = :uploadedBy, a.updatedAt = :now "
            + "WHERE a.id = :id AND a.status = :pending")
    int finalizeStored(@Param("id") UUID id,
                       @Param("pending") PoAttachmentStatus pending,
                       @Param("stored") PoAttachmentStatus stored,
                       @Param("contentType") String contentType,
                       @Param("byteSize") int byteSize,
                       @Param("uploadedBy") UUID uploadedBy,
                       @Param("now") java.time.Instant now);
}
