package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.AnswerQuestionRequest;
import com.builddash.backend.api.dto.request.AskQuestionRequest;
import com.builddash.backend.api.dto.request.CreateSupportTicketRequest;
import com.builddash.backend.api.dto.request.SubmitReviewRequest;
import com.builddash.backend.domain.enums.SupportTicketCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadValidationSizeLimitsTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void submitReviewRequest_commentWithinLimit_valid() {
        SubmitReviewRequest request = new SubmitReviewRequest(5, "a".repeat(2000));
        Set<ConstraintViolation<SubmitReviewRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void submitReviewRequest_commentExceeds2000_rejected() {
        SubmitReviewRequest request = new SubmitReviewRequest(5, "a".repeat(2001));
        Set<ConstraintViolation<SubmitReviewRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("2000");
    }

    @Test
    void askQuestionRequest_bodyWithinLimit_valid() {
        AskQuestionRequest request = new AskQuestionRequest("a".repeat(1000));
        Set<ConstraintViolation<AskQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void askQuestionRequest_bodyExceeds1000_rejected() {
        AskQuestionRequest request = new AskQuestionRequest("a".repeat(1001));
        Set<ConstraintViolation<AskQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("1000");
    }

    @Test
    void answerQuestionRequest_bodyWithinLimit_valid() {
        AnswerQuestionRequest request = new AnswerQuestionRequest("a".repeat(2000));
        Set<ConstraintViolation<AnswerQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void answerQuestionRequest_bodyExceeds2000_rejected() {
        AnswerQuestionRequest request = new AnswerQuestionRequest("a".repeat(2001));
        Set<ConstraintViolation<AnswerQuestionRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("2000");
    }

    @Test
    void createSupportTicketRequest_messageWithinLimit_valid() {
        CreateSupportTicketRequest request = new CreateSupportTicketRequest(
                SupportTicketCategory.ORDER_ISSUE,
                "Valid Subject",
                "a".repeat(5000)
        );
        Set<ConstraintViolation<CreateSupportTicketRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    void createSupportTicketRequest_messageExceeds5000_rejected() {
        CreateSupportTicketRequest request = new CreateSupportTicketRequest(
                SupportTicketCategory.ORDER_ISSUE,
                "Valid Subject",
                "a".repeat(5001)
        );
        Set<ConstraintViolation<CreateSupportTicketRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("5000");
    }
}
