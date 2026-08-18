package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.Question;

final class QuestionMapper {

    private QuestionMapper() {
    }

    static Question toDomain(QuestionEntity entity) {
        Question question = new Question();
        question.setId(entity.getId());
        question.setProductId(entity.getProductId());
        question.setUserId(entity.getUserId());
        question.setBody(entity.getBody());
        question.setCreatedAt(entity.getCreatedAt());
        return question;
    }

    static QuestionEntity toEntity(Question question) {
        QuestionEntity entity = new QuestionEntity();
        entity.setId(question.getId());
        entity.setProductId(question.getProductId());
        entity.setUserId(question.getUserId());
        entity.setBody(question.getBody());
        entity.setCreatedAt(question.getCreatedAt());
        return entity;
    }
}
