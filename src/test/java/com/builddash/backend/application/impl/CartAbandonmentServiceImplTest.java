package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.NotificationService;
import com.builddash.backend.domain.enums.CartType;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartAbandonmentServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private CartAbandonmentServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID cartId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CartAbandonmentServiceImpl(cartRepository, userRepository, notificationService);
        ReflectionTestUtils.setField(service, "afterMinutes", 60L);
        ReflectionTestUtils.setField(service, "renotifyCooldownHours", 24L);
    }

    private Cart cart() {
        return new Cart(cartId, userId, null, CartType.PRIMARY, null, List.of());
    }

    private User user() {
        User user = new User();
        user.setId(userId);
        user.setPhone("+911234567890");
        return user;
    }

    @Test
    void cutoffMath_subtractsConfiguredThresholdFromNow() {
        Instant before = Instant.now();
        when(cartRepository.findStalePrimaryCarts(any())).thenReturn(List.of());

        service.sweepAbandonedCarts();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(cartRepository).findStalePrimaryCarts(cutoffCaptor.capture());
        Instant after = Instant.now();
        assertThat(cutoffCaptor.getValue()).isBetween(before.minus(Duration.ofMinutes(60)), after.minus(Duration.ofMinutes(60)));
    }

    @Test
    void staleCartWithPhone_notifiesViaRecurringWithConfiguredCooldown() {
        when(cartRepository.findStalePrimaryCarts(any())).thenReturn(List.of(cart()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));

        service.sweepAbandonedCarts();

        verify(notificationService).notifyRecurring(userId, NotificationEventType.CART_ABANDONED, cartId,
                Duration.ofHours(24));
    }

    @Test
    void guestUser_skippedWithoutNotification() {
        User guest = user();
        guest.setGuest(true);
        when(cartRepository.findStalePrimaryCarts(any())).thenReturn(List.of(cart()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(guest));

        service.sweepAbandonedCarts();

        verify(notificationService, never()).notifyRecurring(any(), any(), any(), any());
    }

    @Test
    void userWithoutPhone_skippedWithoutNotification() {
        User phoneless = user();
        phoneless.setPhone(null);
        when(cartRepository.findStalePrimaryCarts(any())).thenReturn(List.of(cart()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(phoneless));

        service.sweepAbandonedCarts();

        verify(notificationService, never()).notifyRecurring(any(), any(), any(), any());
    }

    @Test
    void oneCartFailure_doesNotBlockSiblingCarts() {
        UUID otherCartId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Cart other = new Cart(otherCartId, otherUserId, null, CartType.PRIMARY, null, List.of());
        when(cartRepository.findStalePrimaryCarts(any())).thenReturn(List.of(cart(), other));
        when(userRepository.findById(userId)).thenThrow(new IllegalStateException("db hiccup"));
        User otherUser = new User();
        otherUser.setId(otherUserId);
        otherUser.setPhone("+919876543210");
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));

        assertThatCode(() -> service.sweepAbandonedCarts()).doesNotThrowAnyException();

        verify(notificationService).notifyRecurring(otherUserId, NotificationEventType.CART_ABANDONED, otherCartId,
                Duration.ofHours(24));
    }

    @Test
    void cooldownDuration_flowsFromConfiguredHours() {
        ReflectionTestUtils.setField(service, "renotifyCooldownHours", 48L);
        when(cartRepository.findStalePrimaryCarts(any())).thenReturn(List.of(cart()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user()));

        service.sweepAbandonedCarts();

        verify(notificationService).notifyRecurring(userId, NotificationEventType.CART_ABANDONED, cartId,
                Duration.ofHours(48));
    }
}
