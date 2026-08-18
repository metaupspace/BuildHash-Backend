package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.CatalogEventPublisher;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two distinct recovery guarantees, deliberately kept as separate tests:
 *  - nack-and-retry: a flaky publish leaves a row PENDING, a later poll recovers it.
 *  - crash-before-first-relay: a row that no relay run has ever touched yet (the exact
 *    crash-window the outbox pattern exists to close — process died after the domain commit,
 *    before the relay ever ran) is still picked up on the next relay() call.
 */
class CatalogOutboxRelayJpaIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CatalogOutboxEventRepository catalogOutboxEventRepository;

    private UUID saveProduct() {
        Category category = new Category();
        category.setName("Cement");
        category.setSlug("cement-" + UUID.randomUUID());
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Product");
        product.setSlug("product-" + UUID.randomUUID());
        product.setCategoryId(savedCategory.getId());
        product.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(product).getId();
    }

    private UUID seedPendingEvent(UUID productId) {
        CatalogOutboxEvent event = new CatalogOutboxEvent();
        event.setProductId(productId);
        event.setEventType(CatalogOutboxEvent.EVENT_TYPE_PRODUCT_UPSERTED);
        event.setPayload("{\"productId\":\"" + productId + "\"}");
        return catalogOutboxEventRepository.save(event).getId();
    }

    private OutboxStatus statusOf(UUID eventId) {
        return catalogOutboxEventRepository.findByStatus(OutboxStatus.PENDING).stream()
                .anyMatch(e -> e.getId().equals(eventId))
                ? OutboxStatus.PENDING
                : OutboxStatus.PUBLISHED;
    }

    @Test
    void relay_nackForOneRow_leavesItPendingButPublishesTheOther_thenRecoversOnNextPoll() {
        UUID productId = saveProduct();
        UUID okEventId = seedPendingEvent(productId);
        UUID failingEventId = seedPendingEvent(productId);

        FakeCatalogEventPublisher flakyPublisher = new FakeCatalogEventPublisher(Set.of(failingEventId));
        CatalogOutboxRelay relay = new CatalogOutboxRelay(catalogOutboxEventRepository, flakyPublisher);

        relay.relay();

        assertThat(statusOf(okEventId)).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(statusOf(failingEventId)).isEqualTo(OutboxStatus.PENDING);

        // "Restart" — the transient failure is gone, a later poll recovers the still-PENDING row.
        FakeCatalogEventPublisher recoveredPublisher = new FakeCatalogEventPublisher(Set.of());
        CatalogOutboxRelay recoveredRelay = new CatalogOutboxRelay(catalogOutboxEventRepository, recoveredPublisher);
        recoveredRelay.relay();

        assertThat(statusOf(failingEventId)).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void relay_eventSeededDirectlyWithNoPriorRelayRun_isStillPickedUpAndPublished() {
        // Simulates the actual crash window: the domain write + outbox row committed, then the
        // process died before the relay ever ran even once for this row.
        UUID productId = saveProduct();
        UUID eventId = seedPendingEvent(productId);

        CatalogOutboxRelay firstEverRelayRun = new CatalogOutboxRelay(catalogOutboxEventRepository,
                new FakeCatalogEventPublisher(Set.of()));

        firstEverRelayRun.relay();

        assertThat(statusOf(eventId)).isEqualTo(OutboxStatus.PUBLISHED);
    }

    private static final class FakeCatalogEventPublisher implements CatalogEventPublisher {

        private final Set<UUID> failFor;

        private FakeCatalogEventPublisher(Set<UUID> failFor) {
            this.failFor = failFor;
        }

        @Override
        public boolean publishProductChanged(UUID correlationId, String payloadJson) {
            return !failFor.contains(correlationId);
        }
    }
}
