package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.AnswerSource;
import com.builddash.backend.domain.enums.ModerationStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class Answer {

    private UUID id;
    private UUID questionId;
    private UUID userId;
    private String body;
    private AnswerSource source;
    private ModerationStatus status = ModerationStatus.APPROVED;
    private Instant createdAt;
}
