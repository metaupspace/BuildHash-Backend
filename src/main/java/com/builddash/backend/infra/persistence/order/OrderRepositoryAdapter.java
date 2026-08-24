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
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Order> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public List<UUID> findStalePaymentPendingOrderIds(Instant cutoff) {
        return jpaRepository.findStalePaymentPendingOrderIds(OrderStatus.PAYMENT_PENDING, cutoff);
    }

    @Override
    public List<Order> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserIdOrderByPlacedAtDesc(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
