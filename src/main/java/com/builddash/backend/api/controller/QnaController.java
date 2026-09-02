package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.api.dto.request.AnswerQuestionRequest;
import com.builddash.backend.api.dto.request.AskQuestionRequest;
import com.builddash.backend.api.dto.response.AnswerResponse;
import com.builddash.backend.api.dto.response.QuestionResponse;
import com.builddash.backend.api.mapper.QnaMapper;
import com.builddash.backend.application.service.QnaReader;
import com.builddash.backend.application.service.QnaWriter;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.Question;
import com.builddash.backend.domain.model.QuestionThread;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RestController
@Tag(name = "Q&A", description = "Product questions and answers")
@RequiredArgsConstructor
public class QnaController {

    private final QnaReader qnaReader;
    private final QnaWriter qnaWriter;
    private final QnaMapper qnaMapper;


    @GetMapping("/products/{id}/questions")
    @Operation(summary = "List a product's questions with their answers")
    public List<QuestionResponse> listQuestions(
            @PathVariable String id,
            @org.springframework.web.bind.annotation.RequestParam(name = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(name = "size", defaultValue = "20") int size) {
        return qnaMapper.toResponseList(qnaReader.listThreads(parseProductId(id), page, size));
    }

    @PostMapping("/products/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask a question about a product")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Question created"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuestionResponse askQuestion(@PathVariable String id,
                                         @Valid @RequestBody AskQuestionRequest request,
                                         @AuthenticationPrincipal AuthenticatedUser principal) {
        Question question = qnaWriter.ask(parseProductId(id), principal.userId(), request.body());
        return qnaMapper.toResponse(new QuestionThread(question, List.of()));
    }

    @PostMapping("/questions/{id}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Answer a question",
            description = "answer.source is resolved server-side from the caller's JWT roles (vendor/staff/customer), never client-supplied.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Answer created"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Question not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AnswerResponse answerQuestion(@PathVariable String id,
                                          @Valid @RequestBody AnswerQuestionRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser principal) {
        return qnaMapper.toResponse(
                qnaWriter.answer(parseQuestionId(id), principal.userId(), request.body(), principal.roles()));
    }

    private UUID parseProductId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + id);
        }
    }

    private UUID parseQuestionId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("QUESTION_NOT_FOUND", "Question not found: " + id);
        }
    }
}
