package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.LoginEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoginEventJpaRepository extends JpaRepository<LoginEventEntity, UUID> {

    List<LoginEventEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<LoginEventEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    void deleteByUserId(UUID userId);
}
