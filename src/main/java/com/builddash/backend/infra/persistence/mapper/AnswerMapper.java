package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Answer;
import com.builddash.backend.infra.persistence.entity.AnswerEntity;

public final class AnswerMapper {

    private AnswerMapper() {
    }

    public static Answer toDomain(AnswerEntity entity) {
        Answer answer = new Answer();
        answer.setId(entity.getId());
        answer.setQuestionId(entity.getQuestionId());
        answer.setUserId(entity.getUserId());
        answer.setBody(entity.getBody());
        answer.setSource(entity.getSource());
        answer.setStatus(entity.getStatus());
        answer.setCreatedAt(entity.getCreatedAt());
        return answer;
    }

    public static AnswerEntity toEntity(Answer answer) {
        AnswerEntity entity = new AnswerEntity();
        entity.setId(answer.getId());
        entity.setQuestionId(answer.getQuestionId());
        entity.setUserId(answer.getUserId());
        entity.setBody(answer.getBody());
        entity.setSource(answer.getSource());
        entity.setStatus(answer.getStatus());
        entity.setCreatedAt(answer.getCreatedAt());
        return entity;
    }
}
