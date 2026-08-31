package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.AnswerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnswerJpaRepository extends JpaRepository<AnswerEntity, UUID> {

    List<AnswerEntity> findByQuestionIdIn(List<UUID> questionIds);

    java.util.List<AnswerEntity> findByUserId(UUID userId);
}
