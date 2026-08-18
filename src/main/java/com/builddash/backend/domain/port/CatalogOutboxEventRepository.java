package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.OutboxStatus;
import com.builddash.backend.domain.model.CatalogOutboxEvent;

import java.util.List;
import java.util.UUID;

public interface CatalogOutboxEventRepository {

    CatalogOutboxEvent save(CatalogOutboxEvent event);

    List<CatalogOutboxEvent> findByStatus(OutboxStatus status);

    /** Bulk update, not fetch-then-mutate — never relies on JPA dirty-checking. */
    void markPublished(UUID id);
}
