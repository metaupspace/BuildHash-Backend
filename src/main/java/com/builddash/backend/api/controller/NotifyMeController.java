package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.api.dto.response.NotifyMeSubscriptionResponse;
import com.builddash.backend.api.mapper.NotifyMeMapper;
import com.builddash.backend.application.impl.NotifyMeSubscriptionService;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "Back in stock", description = "Notify-me subscriptions for out-of-stock products")
@SecurityRequirement(name = "bearerAuth")
public class NotifyMeController {

    private final NotifyMeSubscriptionService notifyMeSubscriptionService;
    private final NotifyMeMapper notifyMeMapper;

    public NotifyMeController(NotifyMeSubscriptionService notifyMeSubscriptionService, NotifyMeMapper notifyMeMapper) {
        this.notifyMeSubscriptionService = notifyMeSubscriptionService;
        this.notifyMeMapper = notifyMeMapper;
    }

    @PostMapping("/products/{id}/notify-me")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subscribe to a back-in-stock notification",
            description = "Idempotent — subscribing twice returns the existing subscription. Note: no job fires " +
                    "the actual notification yet in Phase 1 — Product.stock has no real inventory-update event to trigger from.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subscription created (or already existed)"),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public NotifyMeSubscriptionResponse subscribe(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser principal) {
        return notifyMeMapper.toResponse(notifyMeSubscriptionService.subscribe(parseProductId(id), principal.userId()));
    }

    private UUID parseProductId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + id);
        }
    }
}
