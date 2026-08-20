package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.AnswerSource;
import com.builddash.backend.domain.enums.ModerationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "answers")
@Getter
@Setter
@NoArgsConstructor
public class AnswerEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "user_id")
    private UUID userId;

    private String body;

    @Enumerated(EnumType.STRING)
    private AnswerSource source;

    @Enumerated(EnumType.STRING)
    private ModerationStatus status = ModerationStatus.APPROVED;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
