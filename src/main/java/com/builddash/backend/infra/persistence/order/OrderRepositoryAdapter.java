package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;
    private final OrderMapper mapper;

    @Override
    public Order save(Order order) {
        OrderEntity saved = jpaRepository.save(mapper.toEntity(order));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findByIdWithLineItems(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Order> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Optional<UUID> findOrderOwnerId(UUID id) {
        return jpaRepository.findOrderOwnerId(id);
    }

    @Override
    public List<UUID> findStalePaymentPendingOrderIds(Instant cutoff) {
        return jpaRepository.findStalePaymentPendingOrderIds(OrderStatus.PAYMENT_PENDING, cutoff);
    }

    @Override
    public boolean existsByAddressId(UUID addressId) {
        return jpaRepository.existsByAddressId(addressId);
    }

    @Override
    public List<Order> findAllByUserId(UUID userId) {
        return findAllByUserId(userId, 0, 20);
    }

    @Override
    public List<Order> findAllByUserId(UUID userId, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 50);
        return jpaRepository.findAllByUserIdOrderByPlacedAtDesc(userId, org.springframework.data.domain.PageRequest.of(boundedPage, boundedSize)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countActiveOrdersForSite(UUID siteId) {
        return jpaRepository.countActiveOrdersForSite(siteId, OrderStatus.CANCELLED);
    }
}
