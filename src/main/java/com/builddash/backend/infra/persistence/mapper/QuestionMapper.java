package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Question;
import com.builddash.backend.infra.persistence.entity.QuestionEntity;

public final class QuestionMapper {

    private QuestionMapper() {
    }

    public static Question toDomain(QuestionEntity entity) {
        Question question = new Question();
        question.setId(entity.getId());
        question.setProductId(entity.getProductId());
        question.setUserId(entity.getUserId());
        question.setBody(entity.getBody());
        question.setCreatedAt(entity.getCreatedAt());
        return question;
    }

    public static QuestionEntity toEntity(Question question) {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(question.getId());
        entity.setProductId(question.getProductId());
        entity.setUserId(question.getUserId());
        entity.setBody(question.getBody());
        entity.setCreatedAt(question.getCreatedAt());
        return entity;
    }
}
