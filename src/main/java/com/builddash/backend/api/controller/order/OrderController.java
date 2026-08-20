package com.builddash.backend.api.controller.order;

import com.builddash.backend.api.dto.request.CreateOrderRequest;
import com.builddash.backend.api.dto.response.OrderResponse;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.exception.PaymentGatewayException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Order and Payment management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an order and initiate payment")
    public OrderResponse createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
            
        return orderService.create(
                user.userId(),
                request.addressId(),
                request.slotId(),
                request.slotDate(),
                request.expectedTotal(),
                idempotencyKey
        );
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<Map<String, Object>> handleGatewayDown(PaymentGatewayException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "timestamp", Instant.now(),
                "status", HttpStatus.BAD_GATEWAY.value(),
                "error", HttpStatus.BAD_GATEWAY.getReasonPhrase(),
                "code", ex.getCode(),
                "message", "Order created but payment gateway failed: " + ex.getMessage(),
                "path", request.getRequestURI(),
                "orderId", ex.getOrderId(),
                "orderStatus", "PAYMENT_PENDING"
        ));
    }
}
