package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.domain.port.CatalogEventPublisher;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * H5.2 Real-PostgreSQL proof:
 * 1. Poison outbox rows (which fail/throw upon publishing) are isolated and do not block healthy sibling rows.
 * 2. Exhausted poison events transition to terminal FAILED status after 5 attempts.
 */
class CatalogOutboxPoisonIsolationJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CatalogOutboxRelay outboxRelay;

    @Autowired
    private CatalogOutboxEventRepository outboxEventRepository;

    @MockBean
    private CatalogEventPublisher catalogEventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID categoryId;
    private UUID healthyProductId;
    private UUID poisonProductId;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Building Supplies', ?, 7)",
                categoryId, "supplies-" + categoryId);

        healthyProductId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Good Bricks', ?, ?, 'ACTIVE', '6901', now(), now())",
                healthyProductId, "bricks-" + healthyProductId, categoryId);

        poisonProductId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Poison Product', ?, ?, 'ACTIVE', '6901', now(), now())",
                poisonProductId, "poison-" + poisonProductId, categoryId);
    }

    @Test
    void poisonOutboxEvent_doesNotBlockHealthyEvents_andTransitionsToFailedAfterMaxAttempts() {
        // 1. Seed one poison event (attempt 4, will hit attempt 5 on this pass) and one healthy event (attempt 0)
        CatalogOutboxEvent poisonEvent = new CatalogOutboxEvent();
        poisonEvent.setProductId(poisonProductId);
        poisonEvent.setEventType(CatalogOutboxEvent.EVENT_TYPE_PRODUCT_UPSERTED);
        poisonEvent.setPayload("{\"malformed\": true}");
        poisonEvent.setStatus(OutboxStatus.PENDING);
        poisonEvent.setAttemptCount(4);
        CatalogOutboxEvent savedPoison = outboxEventRepository.save(poisonEvent);
        UUID poisonEventId = savedPoison.getId();

        CatalogOutboxEvent healthyEvent = new CatalogOutboxEvent();
        healthyEvent.setProductId(healthyProductId);
        healthyEvent.setEventType(CatalogOutboxEvent.EVENT_TYPE_PRODUCT_UPSERTED);
        healthyEvent.setPayload("{\"name\": \"Good Bricks\"}");
        healthyEvent.setStatus(OutboxStatus.PENDING);
        healthyEvent.setAttemptCount(0);
        CatalogOutboxEvent savedHealthy = outboxEventRepository.save(healthyEvent);
        UUID healthyEventId = savedHealthy.getId();

        // 2. Configure publisher: poison throws, healthy returns true (acked)
        when(catalogEventPublisher.publishProductChanged(eq(poisonEventId), eq(poisonEvent.getPayload())))
                .thenThrow(new RuntimeException("Serialization failure on poison payload"));
        when(catalogEventPublisher.publishProductChanged(eq(healthyEventId), eq(healthyEvent.getPayload())))
                .thenReturn(true);

        // 3. Trigger relay sweep
        outboxRelay.relay();

        // 4. Assert healthy event is marked PUBLISHED
        String healthyStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM catalog_outbox_events WHERE id = ?", String.class, healthyEventId);
        assertThat(healthyStatus).isEqualTo("PUBLISHED");

        // 5. Assert poison event transitioned to terminal FAILED (since attemptCount reached 5)
        String poisonStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM catalog_outbox_events WHERE id = ?", String.class, poisonEventId);
        assertThat(poisonStatus).isEqualTo("FAILED");

        Integer poisonAttempts = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM catalog_outbox_events WHERE id = ?", Integer.class, poisonEventId);
        assertThat(poisonAttempts).isEqualTo(5);

        String errorMessage = jdbcTemplate.queryForObject(
                "SELECT error_message FROM catalog_outbox_events WHERE id = ?", String.class, poisonEventId);
        assertThat(errorMessage).contains("Serialization failure");
    }
}
