package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.RfqStatus;
import com.builddash.backend.infra.persistence.entity.RfqEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RfqJpaRepository extends JpaRepository<RfqEntity, UUID> {

    /** The serialization point for quote submission, cancel and convert (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RfqEntity r WHERE r.id = :id")
    Optional<RfqEntity> findByIdForUpdate(@Param("id") UUID id);

    /**
     * One conditional UPDATE, no per-row loop: exactly the SQL
     * UPDATE rfqs SET status='EXPIRED', updated_at=now()
     * WHERE status='OPEN' AND expires_at <= :now
     */
    @Modifying
    @Query("UPDATE RfqEntity r SET r.status = :expired, r.updatedAt = :now "
            + "WHERE r.status = :open AND r.expiresAt <= :now")
    int expireOpenBefore(@Param("now") Instant now,
                         @Param("open") RfqStatus open,
                         @Param("expired") RfqStatus expired);
}
