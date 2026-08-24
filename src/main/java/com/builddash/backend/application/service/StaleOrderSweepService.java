package com.builddash.backend.application.service;

public interface StaleOrderSweepService {
    void sweepStaleOrders();

    /**
     * Releases delivery-slot locks whose TTL passed without becoming an order —
     * decrements their counters and marks them EXPIRED. Without this, every
     * abandoned checkout intent permanently eats one unit of slot capacity.
     */
    void sweepExpiredLocks();
}
