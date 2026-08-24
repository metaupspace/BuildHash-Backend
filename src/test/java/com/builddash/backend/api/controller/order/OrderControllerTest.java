package com.builddash.backend.api.controller.order;

import com.builddash.backend.api.dto.request.CreateOrderRequest;
import com.builddash.backend.api.dto.response.OrderResponse;
import com.builddash.backend.api.mapper.OrderDtoMapper;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.application.service.OrderResult;
import com.builddash.backend.application.service.ReorderResult;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.PaymentGatewayException;
import com.builddash.backend.domain.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private OrderDtoMapper orderMapper;

    @InjectMocks
    private OrderController orderController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().isAssignableFrom(AuthenticatedUser.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                        return new AuthenticatedUser(userId, UUID.randomUUID(), java.util.List.of("GUEST"));
                    }
                })
                .build();
        objectMapper.findAndRegisterModules();
    }



    @Test
    void createOrder_happyPath_returnsCreatedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), new BigDecimal("100.00"), OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, java.util.List.of());
        OrderResult result = new OrderResult(order, "url-1");
        OrderResponse response = new OrderResponse(orderId, "PAYMENT_PENDING", new BigDecimal("100.00"), "url-1", java.time.Instant.now(), null, null, java.util.List.of());

        when(orderService.create(eq(userId), any(), any(), any(), any(), eq("key-1"))).thenReturn(result);
        when(orderMapper.toResponse(result)).thenReturn(response);

        CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), new BigDecimal("100.00"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.paymentUrl").value("url-1"));
    }




    @Test
    void getOrder_happyPath_returnsOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), new BigDecimal("100.00"), OrderStatus.CONFIRMED, UUID.randomUUID(), java.time.Instant.now(), null, null, java.util.List.of());
        OrderResponse response = new OrderResponse(orderId, "CONFIRMED", new BigDecimal("100.00"), null, java.time.Instant.now(), null, null, java.util.List.of());

        when(orderService.getOrder(userId, orderId)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }



    @Test
    void listOrders_happyPath_returnsOrders() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), new BigDecimal("100.00"), OrderStatus.CONFIRMED, UUID.randomUUID(), java.time.Instant.now(), null, null, java.util.List.of());
        OrderResponse response = new OrderResponse(orderId, "CONFIRMED", new BigDecimal("100.00"), null, java.time.Instant.now(), null, null, java.util.List.of());

        when(orderService.listOrders(userId)).thenReturn(java.util.List.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId.toString()));
    }



    @Test
    void reorder_happyPath_returnsCartId() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        ReorderResult result = new ReorderResult(cartId, "Items added to cart");
        when(orderService.reorder(userId, orderId)).thenReturn(result);

        mockMvc.perform(post("/orders/" + orderId + "/reorder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartId").exists());
    }


    @Test
    void createOrder_whenPaymentGatewayFails_returnsBadGatewayWithOrderId() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.create(eq(userId), any(), any(), any(), any(), eq("key-1")))
                .thenThrow(new PaymentGatewayException(orderId, "Connection timeout"));

        CreateOrderRequest request = new CreateOrderRequest(UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), new BigDecimal("100.00"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.orderStatus").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_DOWN"))
                .andExpect(jsonPath("$.message").value("Order created but payment gateway failed: Connection timeout"));
    }
}
