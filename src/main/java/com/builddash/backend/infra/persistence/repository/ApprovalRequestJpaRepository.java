package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.ApprovalRequestEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestJpaRepository extends JpaRepository<ApprovalRequestEntity, UUID> {

    Optional<ApprovalRequestEntity> findById(UUID id);

    Optional<ApprovalRequestEntity> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ApprovalRequestEntity r WHERE r.id = :id")
    Optional<ApprovalRequestEntity> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ApprovalRequestEntity r WHERE r.orderId = :orderId")
    Optional<ApprovalRequestEntity> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    /**
     * Due-for-escalation candidates. NULL escalation_due_at compares false under <=,
     * so blocked requests (dueAt cleared) are never selected again.
     */
    @Query("SELECT r.id FROM ApprovalRequestEntity r " +
            "WHERE r.status = 'PENDING' AND r.escalationDueAt <= :now")
    List<UUID> findDueIds(@Param("now") Instant now);

    List<ApprovalRequestEntity> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<ApprovalRequestEntity> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, org.springframework.data.domain.Pageable pageable);

    List<ApprovalRequestEntity> findByCompanyIdAndSiteIdInOrderByCreatedAtDesc(
            UUID companyId, Collection<UUID> siteIds);

    List<ApprovalRequestEntity> findByCompanyIdAndSiteIdInOrderByCreatedAtDesc(
            UUID companyId, Collection<UUID> siteIds, org.springframework.data.domain.Pageable pageable);
}
