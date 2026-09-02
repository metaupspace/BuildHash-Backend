package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.QnaReader;
import com.builddash.backend.application.service.QnaWriter;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Answer;
import com.builddash.backend.domain.model.Question;
import com.builddash.backend.domain.model.QuestionThread;
import com.builddash.backend.domain.port.AnswerRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.QuestionRepository;
import com.builddash.backend.domain.service.AnswerSourceResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class QnaServiceImpl implements QnaReader, QnaWriter {

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final ProductRepository productRepository;
    private final AnswerSourceResolver answerSourceResolver;


    /**
     * Batch-fetches every answer for the product's questions in one query
     * (AnswerRepository.findByQuestionIdIn) — never loop-fetch per question, that's an N+1.
     */
    @Override
    public List<QuestionThread> listThreads(UUID productId) {
        return listThreads(productId, 0, 20);
    }

    @Override
    public List<QuestionThread> listThreads(UUID productId, int page, int size) {
        List<Question> questions = questionRepository.findByProductId(productId, page, size);
        if (questions.isEmpty()) {
            return List.of();
        }

        List<UUID> questionIds = questions.stream().map(Question::getId).toList();
        Map<UUID, List<Answer>> answersByQuestionId = answerRepository.findByQuestionIdIn(questionIds).stream()
                .collect(Collectors.groupingBy(Answer::getQuestionId));

        return questions.stream()
                .map(question -> new QuestionThread(question,
                        answersByQuestionId.getOrDefault(question.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public Question ask(UUID productId, UUID userId, String body) {
        productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + productId));

        Question question = new Question();
        question.setProductId(productId);
        question.setUserId(userId);
        question.setBody(body);
        return questionRepository.save(question);
    }

    @Override
    @Transactional
    public Answer answer(UUID questionId, UUID userId, String body, List<String> roles) {
        questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("QUESTION_NOT_FOUND", "Question not found: " + questionId));

        Answer answer = new Answer();
        answer.setQuestionId(questionId);
        answer.setUserId(userId);
        answer.setBody(body);
        answer.setSource(answerSourceResolver.resolve(roles));
        return answerRepository.save(answer);
    }
}
