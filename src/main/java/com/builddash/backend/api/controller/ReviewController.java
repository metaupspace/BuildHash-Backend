package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.api.dto.request.SubmitReviewRequest;
import com.builddash.backend.api.dto.response.ReviewResponse;
import com.builddash.backend.api.mapper.ReviewMapper;
import com.builddash.backend.application.service.ReviewReader;
import com.builddash.backend.application.service.ReviewWriter;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.exception.NotFoundException;
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

@RestController
@Tag(name = "Reviews", description = "Product reviews")
public class ReviewController {

    private final ReviewReader reviewReader;
    private final ReviewWriter reviewWriter;
    private final ReviewMapper reviewMapper;

    public ReviewController(ReviewReader reviewReader, ReviewWriter reviewWriter, ReviewMapper reviewMapper) {
        this.reviewReader = reviewReader;
        this.reviewWriter = reviewWriter;
        this.reviewMapper = reviewMapper;
    }

    @GetMapping("/products/{id}/reviews")
    @Operation(summary = "List a product's approved reviews")
    public List<ReviewResponse> listReviews(@PathVariable String id) {
        return reviewMapper.toResponseList(reviewReader.listApproved(parseProductId(id)));
    }

    @PostMapping("/products/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a review")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Review created"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ReviewResponse submitReview(@PathVariable String id,
                                        @Valid @RequestBody SubmitReviewRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser principal) {
        return reviewMapper.toResponse(
                reviewWriter.submit(parseProductId(id), principal.userId(), request.rating(), request.comment()));
    }

    private UUID parseProductId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + id);
        }
    }
}
