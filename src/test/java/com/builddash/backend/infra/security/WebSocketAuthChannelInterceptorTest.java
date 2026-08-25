package com.builddash.backend.infra.security;

import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.TokenClaims;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private TokenValidator tokenValidator;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MessageChannel messageChannel;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(tokenValidator, orderRepository);
    }

    @Test
    void preSend_connect_withValidBearerToken_authenticatesUser() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        String rawToken = "valid.jwt.token";

        when(tokenValidator.validate(rawToken, TokenType.ACCESS))
                .thenReturn(new TokenClaims(userId, deviceId, List.of("USER")));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + rawToken);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Authentication auth = (Authentication) resultAccessor.getUser();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
        assertThat(user.userId()).isEqualTo(userId);
    }

    @Test
    void preSend_connect_missingAuthHeader_throwsUnauthorized() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void preSend_connect_invalidToken_throwsUnauthorized() {
        String rawToken = "invalid.token";
        when(tokenValidator.validate(rawToken, TokenType.ACCESS))
                .thenThrow(new UnauthorizedException("UNAUTHENTICATED", "Token expired"));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + rawToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void preSend_subscribe_ownerSubscribesToOrder_succeeds() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = new Order(
                orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("100.00"), OrderStatus.CONFIRMED, UUID.randomUUID(),
                Instant.now(), null, null, List.of()
        );
        when(orderRepository.findOrderOwnerId(orderId)).thenReturn(Optional.of(order.userId()));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/orders/" + orderId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UUID.randomUUID(), List.of("USER")), null, List.of()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, messageChannel);
        assertThat(result).isNotNull();
    }

    @Test
    void preSend_subscribe_nonOwnerSubscribesToOrder_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = new Order(
                orderId, otherUserId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("100.00"), OrderStatus.CONFIRMED, UUID.randomUUID(),
                Instant.now(), null, null, List.of()
        );
        when(orderRepository.findOrderOwnerId(orderId)).thenReturn(Optional.of(order.userId()));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/orders/" + orderId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UUID.randomUUID(), List.of("USER")), null, List.of()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void preSend_subscribe_orderNotFound_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        when(orderRepository.findOrderOwnerId(orderId)).thenReturn(Optional.empty());

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/orders/" + orderId);
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, UUID.randomUUID(), List.of("USER")), null, List.of()));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void preSend_subscribe_unauthenticatedUser_throwsUnauthorized() {
        UUID orderId = UUID.randomUUID();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/orders/" + orderId);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(UnauthorizedException.class);
    }
}
