package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 9-A order tagging on real Postgres: B2B-tagged orders round-trip company/site
 * columns; every B2C order stays company_id/site_id NULL; and confirmed_at is set
 * exclusively by Order.confirm() (creation leaves it null) — the 9-E statement
 * reporting timestamp contract.
 */
class CompanyOrderTaggingIT extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Repository reads are lazy on line items — services read inside a tx, so the IT does too. */
    private Order find(UUID orderId) {
        return transactionTemplate.execute(status -> orderRepository.findById(orderId).orElseThrow());
    }

    private UUID userId;
    private UUID companyId;
    private UUID siteId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'Acme')", companyId);
        jdbcTemplate.update("INSERT INTO company_sites (id, company_id, name) VALUES (?, ?, 'HQ')",
                siteId, companyId);
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug) VALUES (?, 'C', ?)", categoryId, "c" + categoryId);
        productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) "
                        + "VALUES (?, 'P', ?, ?, 'ACTIVE', '2523', now(), now())", productId, "p" + productId, categoryId);
    }

    private Order minimalOrder(UUID company, UUID site) {
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) "
                        + "VALUES (?, ?, 'HOME', 'S', 'C', 'MH', '400001', now(), now())", addressId, userId);
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111103");
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) "
                        + "VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 0) ON CONFLICT DO NOTHING", slotId);
        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 2,
                new BigDecimal("250.00"), new BigDecimal("90.00"), new BigDecimal("590.00"));
        return new Order(UUID.randomUUID(), userId, addressId, slotId, LocalDate.now(),
                new BigDecimal("590.00"), OrderStatus.PAYMENT_PENDING, UUID.randomUUID(),
                Instant.now(), null, null, List.of(item), company, site, null);
    }

    @Test
    void b2bOrder_roundTripsCompanyAndSiteColumns() {
        Order saved = orderRepository.save(minimalOrder(companyId, siteId));

        Order loaded = find(saved.id());
        assertThat(loaded.companyId()).isEqualTo(companyId);
        assertThat(loaded.siteId()).isEqualTo(siteId);
    }

    @Test
    void b2cOrder_companyAndSiteColumnsNull() {
        Order saved = orderRepository.save(minimalOrder(null, null));

        Order loaded = find(saved.id());
        assertThat(loaded.companyId()).isNull();
        assertThat(loaded.siteId()).isNull();
    }

    @Test
    void confirmedAt_nullAtCreation_setOnlyByConfirm() {
        Order created = orderRepository.save(minimalOrder(companyId, siteId));
        assertThat(created.confirmedAt()).isNull();
        assertThat(find(created.id()).confirmedAt()).isNull();

        Order confirmed = orderRepository.save(created.confirm());
        assertThat(confirmed.confirmedAt()).isNotNull();

        Order reloaded = find(confirmed.id());
        assertThat(reloaded.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(reloaded.confirmedAt()).isNotNull();
        // Guards carry tagging through later transitions
        Order packed = orderRepository.save(reloaded.pack());
        assertThat(packed.companyId()).isEqualTo(companyId);
        assertThat(packed.confirmedAt()).isEqualTo(reloaded.confirmedAt());
    }
}
