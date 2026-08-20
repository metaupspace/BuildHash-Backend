package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.AnswerResponse;
import com.builddash.backend.api.dto.response.QuestionResponse;
import com.builddash.backend.domain.model.Answer;
import com.builddash.backend.domain.model.Question;
import com.builddash.backend.domain.model.QuestionThread;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QnaMapper {

    public QuestionResponse toResponse(QuestionThread thread) {
        Question question = thread.question();
        List<AnswerResponse> answers = thread.answers().stream().map(this::toResponse).toList();
        return new QuestionResponse(question.getId(), question.getUserId(), question.getBody(),
                question.getCreatedAt(), answers);
    }

    public List<QuestionResponse> toResponseList(List<QuestionThread> threads) {
        return threads.stream().map(this::toResponse).toList();
    }

    public AnswerResponse toResponse(Answer answer) {
        return new AnswerResponse(answer.getId(), answer.getUserId(), answer.getBody(),
                answer.getSource(), answer.getCreatedAt());
    }
}
