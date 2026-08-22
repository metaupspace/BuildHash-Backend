package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderRepositoryAdapterJpaIT extends AbstractIntegrationTest {

    @Autowired
    private OrderRepositoryAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void saveAndFindById_persistsAndLoadsOrderCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO users (id) VALUES (?)", userId);
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable) VALUES (?, ?, 'HOME', 'A', 'B', 'C', '111', true)", addressId, userId);
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug) VALUES (?, 'Test', 'test')", categoryId);
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status) VALUES (?, 'Test', 'test-prod', ?, 'ACTIVE')", productId, categoryId);

        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 1, new BigDecimal("100.00"), new BigDecimal("18.00"));
        Order order = new Order(orderId, userId, addressId, UUID.randomUUID(), LocalDate.now(), new BigDecimal("118.00"), OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), java.time.Instant.now(), null, null, List.of(item));

        Order saved = adapter.save(order);
        assertThat(saved.id()).isEqualTo(orderId);

        Optional<Order> found = adapter.findById(orderId);
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(orderId);
        assertThat(found.get().lineItems()).hasSize(1);
        assertThat(found.get().lineItems().get(0).unitPrice()).isEqualByComparingTo("100.00");
    }
}
