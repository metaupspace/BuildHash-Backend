package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.QuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionJpaRepository extends JpaRepository<QuestionEntity, UUID> {

    List<QuestionEntity> findByProductId(UUID productId);

    java.util.List<QuestionEntity> findByUserId(UUID userId);
}
