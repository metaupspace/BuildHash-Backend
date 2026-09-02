package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(UUID id);
    Optional<Order> findByIdForUpdate(UUID id);
    /** Lightweight ownership check: selects only the owning userId, never touches line items. */
    Optional<UUID> findOrderOwnerId(UUID orderId);
    List<UUID> findStalePaymentPendingOrderIds(Instant cutoff);
    List<Order> findAllByUserId(UUID userId);
    List<Order> findAllByUserId(UUID userId, int page, int size);
    boolean existsByAddressId(UUID addressId);
    /** Non-CANCELLED order count referencing a company site — the site deactivation guard (9-A). */
    long countActiveOrdersForSite(UUID siteId);
}
