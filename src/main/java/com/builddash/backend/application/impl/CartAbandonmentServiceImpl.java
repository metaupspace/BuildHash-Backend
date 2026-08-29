package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CartAbandonmentService;
import com.builddash.backend.application.service.NotificationService;
import com.builddash.backend.domain.enums.NotificationEventType;
import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.domain.port.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Skips guests and users with no resolvable phone (plan 5(f)) — a phoneless guest session
 * has no channel, which is the plan's named permanent coverage gap, stated rather than
 * hidden. Everything else routes through notifyRecurring: the log's cooldown guard makes
 * re-notification possible after the window while suppressing repeats within it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartAbandonmentServiceImpl implements CartAbandonmentService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Value("${notification.cart-abandonment.after-minutes:60}")
    private long afterMinutes;

    @Value("${notification.cart-abandonment.renotify-cooldown-hours:24}")
    private long renotifyCooldownHours;

    @Override
    @Transactional
    public void sweepAbandonedCarts() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(afterMinutes));
        List<Cart> stale = cartRepository.findStalePrimaryCarts(cutoff);
        for (Cart cart : stale) {
            try {
                notifyOne(cart);
            } catch (Exception e) {
                // Per-cart try/catch (CatalogOutboxRelay discipline): one failing cart
                // never blocks siblings still waiting in this batch.
                log.error("Failed to process abandoned cart {}", cart.id(), e);
            }
        }
    }

    private void notifyOne(Cart cart) {
        User user = userRepository.findById(cart.userId()).orElse(null);
        if (user == null) {
            log.warn("No user {} for abandoned cart {}, skipping", cart.userId(), cart.id());
            return;
        }
        if (user.isGuest()) {
            return;
        }
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            log.info("User {} has no phone, no channel for abandoned cart {}, skipping", user.getId(), cart.id());
            return;
        }
        notificationService.notifyRecurring(cart.userId(), NotificationEventType.CART_ABANDONED, cart.id(),
                Duration.ofHours(renotifyCooldownHours));
    }
}
