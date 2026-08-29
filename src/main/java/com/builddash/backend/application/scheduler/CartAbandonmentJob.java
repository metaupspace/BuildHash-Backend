package com.builddash.backend.application.scheduler;

import com.builddash.backend.application.service.CartAbandonmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Thin scheduler delegating to CartAbandonmentService — StaleOrderSweepJob shape exactly.
 * Poll interval is yaml-configurable (plan OQ-7), default 5 minutes.
 */
@Component
@RequiredArgsConstructor
public class CartAbandonmentJob {

    private final CartAbandonmentService cartAbandonmentService;


    @Scheduled(fixedDelayString = "${notification.cart-abandonment.poll-interval-ms:300000}")
    public void run() {
        cartAbandonmentService.sweepAbandonedCarts();
    }
}
