package com.builddash.backend.infra.security;

import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.UnauthorizedException;
import com.builddash.backend.domain.model.TokenClaims;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.TokenValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern ORDER_TOPIC_PATTERN = Pattern.compile("^/topic/orders/([0-9a-fA-F\\-]+)$");

    private final TokenValidator tokenValidator;
    private final OrderRepository orderRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getFirstNativeHeader("token");
        }
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getFirstNativeHeader("access_token");
        }

        if (authHeader == null || authHeader.isBlank()) {
            log.warn("WebSocket CONNECT rejected: missing authentication header");
            throw new UnauthorizedException("UNAUTHENTICATED", "Missing WebSocket authentication token");
        }

        String token = authHeader.startsWith(BEARER_PREFIX) ? authHeader.substring(BEARER_PREFIX.length()).trim() : authHeader.trim();

        try {
            TokenClaims claims = tokenValidator.validate(token, TokenType.ACCESS);
            AuthenticatedUser principal = new AuthenticatedUser(claims.userId(), claims.deviceId(), claims.roles());
            List<GrantedAuthority> authorities = claims.roles().stream()
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            accessor.setUser(authentication);
            log.debug("WebSocket CONNECT authenticated for user {}", claims.userId());
        } catch (Exception e) {
            log.warn("WebSocket CONNECT rejected: invalid token ({})", e.getMessage());
            throw new UnauthorizedException("UNAUTHENTICATED", "Invalid WebSocket authentication token");
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Matcher matcher = ORDER_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            String orderIdStr = matcher.group(1);
            UUID orderId;
            try {
                orderId = UUID.fromString(orderIdStr);
            } catch (IllegalArgumentException e) {
                log.warn("WebSocket SUBSCRIBE rejected: invalid order UUID in destination {}", destination);
                throw new NotFoundException("Order", orderIdStr);
            }

            Authentication auth = (Authentication) accessor.getUser();
            if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
                log.warn("WebSocket SUBSCRIBE rejected: unauthenticated subscription attempt to {}", destination);
                throw new UnauthorizedException("UNAUTHENTICATED", "User not authenticated for subscription");
            }

            // Lightweight projection: needs only the owning userId — never touches the LAZY
            // lineItems collection, so no open session/transaction is required on this broker thread
            UUID ownerUserId = orderRepository.findOrderOwnerId(orderId)
                    .orElseThrow(() -> new NotFoundException("Order", orderId.toString()));

            if (!ownerUserId.equals(user.userId())) {
                log.warn("WebSocket SUBSCRIBE rejected: user {} does not own order {}", user.userId(), orderId);
                throw new ForbiddenException("FORBIDDEN", "User does not own this order");
            }
            log.debug("WebSocket SUBSCRIBE authorized: user {} subscribed to {}", user.userId(), destination);
        }
    }
}
