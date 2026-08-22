package com.builddash.backend.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.SearchIndex;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Only ever writes through the "products" alias (PLAN_PHASE1.md Section 3) — index creation
 * and alias management belong to the blue-green reindex job (3c), not this write path.
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class ElasticsearchSearchIndexAdapter implements SearchIndex {
    private static final String PRODUCTS_ALIAS = "products";
    private static final int VERSION_CONFLICT_STATUS = 409;

    private final ElasticsearchClient client;


    @Override
    public void upsertProduct(ProductSyncPayload payload) {
        try {
            client.index(i -> i
                    .index(PRODUCTS_ALIAS)
                    .id(payload.productId().toString())
                    .document(payload)
                    .versionType(VersionType.External)
                    .version(payload.updatedAtEpochMillis()));
        } catch (ElasticsearchException e) {
            if (e.status() == VERSION_CONFLICT_STATUS) {
                log.debug("Ignoring stale write for product {} (version {} not newer than stored)",
                        payload.productId(), payload.updatedAtEpochMillis());
                return;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to upsert product " + payload.productId() + " into search index", e);
        }
    }
}
