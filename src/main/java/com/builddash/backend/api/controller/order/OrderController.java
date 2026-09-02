package com.builddash.backend.api.controller.order;

import com.builddash.backend.api.dto.request.CreateOrderRequest;
import com.builddash.backend.api.dto.request.DeliveryStatusUpdateRequest;
import com.builddash.backend.api.dto.request.RescheduleOrderRequest;
import com.builddash.backend.api.dto.response.CallDriverResponse;
import com.builddash.backend.api.dto.response.OrderResponse;
import com.builddash.backend.api.dto.response.OrderTrackingResponse;
import com.builddash.backend.api.dto.response.ReorderResponse;
import com.builddash.backend.api.mapper.OrderDtoMapper;
import com.builddash.backend.api.mapper.OrderTrackingDtoMapper;
import com.builddash.backend.application.service.OrderResult;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.application.service.OrderTrackingService;
import com.builddash.backend.application.service.ReorderResult;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Order and Payment management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderTrackingService orderTrackingService;
    private final OrderDtoMapper orderMapper;
    private final OrderTrackingDtoMapper orderTrackingDtoMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an order and initiate payment")
    public OrderResponse createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        OrderResult result = orderService.create(
                user.userId(),
                request.addressId(),
                request.slotId(),
                request.slotDate(),
                request.expectedTotal(),
                request.cartId(),
                request.siteId(),
                idempotencyKey
        );
        return orderMapper.toResponse(result);
    }

    @PostMapping("/{id}/payments/retry")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Retry payment for an existing PAYMENT_PENDING order")
    public OrderResponse retryPayment(
            @PathVariable("id") UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        OrderResult result = orderService.retryPayment(user.userId(), orderId);
        return orderMapper.toResponse(result);
    }

    @GetMapping
    @Operation(summary = "List customer's orders")
    public List<OrderResponse> listOrders(
            @AuthenticationPrincipal AuthenticatedUser user,
            @org.springframework.web.bind.annotation.RequestParam(name = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(name = "size", defaultValue = "20") int size) {
        return orderService.listOrders(user.userId(), page, size).stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get specific order details")
    public OrderResponse getOrder(
            @PathVariable("id") UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return orderMapper.toResponse(orderService.getOrder(user.userId(), orderId));
    }

    @PostMapping("/{id}/reorder")
    @Operation(summary = "Add an existing order's items to the cart")
    public ReorderResponse reorder(
            @PathVariable("id") UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        ReorderResult result = orderService.reorder(user.userId(), orderId);
        return new ReorderResponse(result.cartId(), result.message());
    }

    @GetMapping("/{id}/tracking")
    @Operation(summary = "Get tracking information for an order")
    public OrderTrackingResponse getTracking(
            @PathVariable("id") UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return orderTrackingDtoMapper.toResponse(orderTrackingService.getTracking(user.userId(), orderId));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Delivery partner webhook to update order tracking status")
    public ResponseEntity<Void> updateDeliveryStatus(
            @PathVariable("id") UUID orderId,
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody DeliveryStatusUpdateRequest request) {
        orderTrackingService.updateDeliveryStatus(
                orderId,
                request.status(),
                request.driverId(),
                request.driverPhone(),
                request.latitude(),
                request.longitude(),
                apiKey
        );
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reschedule")
    @Operation(summary = "Reschedule a confirmed order within the modification window")
    public ResponseEntity<Void> rescheduleOrder(
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody RescheduleOrderRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        orderTrackingService.rescheduleOrder(user.userId(), orderId, request.newSlotId(), request.slotDate());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a confirmed order within the modification window")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable("id") UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        orderTrackingService.cancelOrderWithinWindow(user.userId(), orderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/call-driver")
    @Operation(summary = "Initiate a masked call proxy with the delivery driver")
    public CallDriverResponse callDriver(
            @PathVariable("id") UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        orderTrackingService.callDriver(user.userId(), orderId);
        return new CallDriverResponse("CALL_INITIATED");
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
