package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.enums.ModerationStatus;
import com.builddash.backend.infra.persistence.entity.ReviewEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewJpaRepository extends JpaRepository<ReviewEntity, UUID> {

    List<ReviewEntity> findByProductIdAndStatus(UUID productId, ModerationStatus status);

    List<ReviewEntity> findByProductIdAndStatus(UUID productId, ModerationStatus status, Pageable pageable);

    java.util.List<ReviewEntity> findByUserId(UUID userId);
}
