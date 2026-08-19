package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ReviewJpaRepository extends JpaRepository<ReviewEntity, UUID> {

    List<ReviewEntity> findByProductIdAndStatus(UUID productId, ModerationStatus status);
}
