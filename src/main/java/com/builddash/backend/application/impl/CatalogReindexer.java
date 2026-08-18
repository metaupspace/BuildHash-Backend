package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductPageCursor;
import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.SearchIndexAdmin;
import com.builddash.backend.domain.service.ProductSyncProjectionBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Blue-green reindex (PLAN_PHASE1.md Section 3), built against a nightly cron trigger only
 * (Open Question #7) — a manual/admin-triggered variant can later call reindex() directly
 * without this class changing. No separate interface: single workflow, same judgment as
 * OtpSendService. Gated out of tests by the existing SchedulingConfig("!test").
 *
 * The same run doubles as the outbox drift sweep (Section 3: "one job, two jobs' worth of
 * drift covered") — safe because any outbox row created *before* this run started is, by
 * construction, already reflected in the backfill that just completed (same Postgres source
 * of truth). Rows created during the run are left for the next cycle, not raced against.
 */
@Service
public class CatalogReindexer {

    private static final String PRODUCTS_ALIAS = "products";
    private static final int PAGE_SIZE = 200;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductSyncProjectionBuilder productSyncProjectionBuilder;
    private final SearchIndexAdmin searchIndexAdmin;
    private final CatalogOutboxEventRepository catalogOutboxEventRepository;

    public CatalogReindexer(ProductRepository productRepository, CategoryRepository categoryRepository,
                             ProductSyncProjectionBuilder productSyncProjectionBuilder, SearchIndexAdmin searchIndexAdmin,
                             CatalogOutboxEventRepository catalogOutboxEventRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productSyncProjectionBuilder = productSyncProjectionBuilder;
        this.searchIndexAdmin = searchIndexAdmin;
        this.catalogOutboxEventRepository = catalogOutboxEventRepository;
    }

    @Scheduled(cron = "${catalog.reindex.cron:0 0 2 * * *}")
    public void reindex() {
        Instant startedAt = Instant.now();

        Map<UUID, Category> categoriesById = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, category -> category));

        String newIndex = searchIndexAdmin.createIndex();
        backfill(newIndex, categoriesById);
        searchIndexAdmin.swapAlias(PRODUCTS_ALIAS, newIndex);

        reconcileOutbox(startedAt);
    }

    private void backfill(String indexName, Map<UUID, Category> categoriesById) {
        ProductPageCursor cursor = null;
        while (true) {
            List<Product> page = productRepository.findPage(null, null, cursor, PAGE_SIZE + 1);
            if (page.isEmpty()) {
                return;
            }

            boolean hasNext = page.size() > PAGE_SIZE;
            List<Product> items = hasNext ? page.subList(0, PAGE_SIZE) : page;

            for (Product product : items) {
                Category category = categoriesById.get(product.getCategoryId());
                ProductSyncPayload payload = productSyncProjectionBuilder.build(product, category);
                searchIndexAdmin.indexDocument(indexName, payload);
            }

            if (!hasNext) {
                return;
            }
            Product last = items.get(items.size() - 1);
            cursor = new ProductPageCursor(last.getCreatedAt(), last.getId());
        }
    }

    private void reconcileOutbox(Instant startedAt) {
        Stream.of(OutboxStatus.PENDING, OutboxStatus.PUBLISHED)
                .flatMap(status -> catalogOutboxEventRepository.findByStatus(status).stream())
                .filter(event -> event.getCreatedAt().isBefore(startedAt))
                .forEach(event -> catalogOutboxEventRepository.markProcessed(event.getId()));
    }
}
