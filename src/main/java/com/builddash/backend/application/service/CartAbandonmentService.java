package com.builddash.backend.application.service;

/**
 * The cart-abandonment trigger (PLAN_PHASE7 5(f)): notify the owner of every stale
 * PRIMARY cart. Dedup/re-notification policy lives in NotificationLog's cooldown guard,
 * invoked through NotificationService.notifyRecurring.
 */
public interface CartAbandonmentService {

    void sweepAbandonedCarts();
}
