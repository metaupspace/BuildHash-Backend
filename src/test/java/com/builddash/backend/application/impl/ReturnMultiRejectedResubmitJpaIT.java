package com.builddash.backend.application.impl;

import com.builddash.backend.api.dto.request.ReturnLineItemRequest;
import com.builddash.backend.application.service.ReturnService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.ReturnAlreadyExistsException;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * H3.4 Real-PostgreSQL proof: multiple historical REJECTED returns for one order
 * never trigger a Spring Data JPA cardinality crash (NonUniqueResultException),
 * findActiveByOrderId safely isolates active returns, and findAllByOrderId returns
 * complete chronological history.
 */
class ReturnMultiRejectedResubmitJpaIT extends AbstractIntegrationTest {

    @Autowired
    private ReturnService returnService;

    @Autowired
    private ReturnRepository returnRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Hardware', ?, 7)",
                categoryId, "hardware-" + categoryId);

        productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Cement Bag', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "cement-" + productId, categoryId);
    }

    private Order deliveredOrder() {
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        UUID lockId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())",
                addressId, userId);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT DO NOTHING", slotId);
        jdbcTemplate.update("INSERT INTO delivery_slot_locks (id, user_id, slot_id, slot_date, expires_at, status) VALUES (?, ?, ?, CURRENT_DATE, CURRENT_TIMESTAMP, 'ACTIVE')",
                lockId, userId, slotId);

        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 3,
                new BigDecimal("350.00"), new BigDecimal("294.00"), new BigDecimal("1344.00"));
        return orderRepository.save(new Order(orderId, userId, addressId, slotId, LocalDate.now(),
                new BigDecimal("1344.00"), OrderStatus.DELIVERED, lockId, Instant.now(), null, null, List.of(item)));
    }

    private List<MultipartFile> photos() {
        return List.of(new MockMultipartFile("photos", "damage.jpg", "image/jpeg", new byte[]{1, 2, 3}));
    }

    private List<ReturnLineItemRequest> items() {
        return List.of(new ReturnLineItemRequest(productId, 1));
    }

    @Test
    void multipleRejectedReturns_allowsSubsequentActiveReturnWithoutCardinalityCrash() {
        Order order = deliveredOrder();

        // 1. First return created and rejected
        Return r1 = returnService.createReturn(userId, order.id(), ReturnReason.DAMAGED, items(), photos());
        returnService.reject(r1.id(), userId, List.of("VENDOR"));

        // 2. Second return created and rejected
        Return r2 = returnService.createReturn(userId, order.id(), ReturnReason.DAMAGED, items(), photos());
        returnService.reject(r2.id(), userId, List.of("ADMIN"));

        // Verify 2 REJECTED rows exist in database
        Integer rejectedCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM returns WHERE order_id = ? AND status = 'REJECTED'", Integer.class, order.id());
        assertThat(rejectedCount).isEqualTo(2);

        // 3. Third return created — previously threw NonUniqueResultException on findByOrderId
        Return r3 = returnService.createReturn(userId, order.id(), ReturnReason.DAMAGED, items(), photos());
        assertThat(r3).isNotNull();
        assertThat(r3.status()).isEqualTo(ReturnStatus.REQUESTED);

        // Verify active and historical counts
        var activeOpt = returnRepository.findActiveByOrderId(order.id());
        assertThat(activeOpt).isPresent();
        assertThat(activeOpt.get().id()).isEqualTo(r3.id());

        List<Return> allReturns = returnRepository.findAllByOrderId(order.id());
        assertThat(allReturns).hasSize(3);

        // 4. Fourth return attempted while third is still active — must throw ReturnAlreadyExistsException
        assertThatThrownBy(() -> returnService.createReturn(userId, order.id(), ReturnReason.DAMAGED, items(), photos()))
                .isInstanceOf(ReturnAlreadyExistsException.class);
    }
}
