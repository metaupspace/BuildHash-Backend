package com.builddash.backend.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface QuestionJpaRepository extends JpaRepository<QuestionEntity, UUID> {

    List<QuestionEntity> findByProductId(UUID productId);
}
