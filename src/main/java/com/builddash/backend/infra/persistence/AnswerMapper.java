package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.Answer;

final class AnswerMapper {

    private AnswerMapper() {
    }

    static Answer toDomain(AnswerEntity entity) {
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

    static AnswerEntity toEntity(Answer answer) {
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
