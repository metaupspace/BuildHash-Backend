package com.builddash.backend.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface AnswerJpaRepository extends JpaRepository<AnswerEntity, UUID> {

    List<AnswerEntity> findByQuestionIdIn(List<UUID> questionIds);
}
