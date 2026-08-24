package com.builddash.backend.support;

import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.SearchIndexAdmin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test-only in-memory index/alias admin: multiple named indices, one alias pointer per alias
 * name. getFromAlias lets a test read "whatever the alias currently resolves to" — the same
 * thing a real client would see — without needing a real Elasticsearch cluster.
 */
public class RecordingSearchIndexAdmin implements SearchIndexAdmin {

    private final Map<String, Map<UUID, ProductSyncPayload>> indices = new ConcurrentHashMap<>();
    private final Map<String, String> aliasToIndex = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public String createIndex() {
        String name = "products_v" + counter.incrementAndGet();
        indices.put(name, new ConcurrentHashMap<>());
        return name;
    }

    @Override
    public void indexDocument(String indexName, ProductSyncPayload payload) {
        indices.get(indexName).put(payload.productId(), payload);
    }

    @Override
    public void swapAlias(String alias, String newIndexName) {
        aliasToIndex.put(alias, newIndexName);
    }

    @Override
    public String resolveAlias(String alias) {
        return aliasToIndex.get(alias);
    }

    @Override
    public void deleteIndex(String indexName) {
        indices.remove(indexName);
        aliasToIndex.values().remove(indexName);
    }

    public ProductSyncPayload getFromAlias(String alias, UUID productId) {
        String index = aliasToIndex.get(alias);
        return index == null ? null : indices.get(index).get(productId);
    }

    public int documentCount(String indexName) {
        Map<UUID, ProductSyncPayload> index = indices.get(indexName);
        return index == null ? 0 : index.size();
    }

    /** Test-only seeding helper — simulates "an index already existed before this test ran". */
    public void seed(String indexName, ProductSyncPayload payload) {
        indices.computeIfAbsent(indexName, k -> new ConcurrentHashMap<>()).put(payload.productId(), payload);
    }

    public List<UUID> productIdsIn(String indexName) {
        return indices.getOrDefault(indexName, Map.of()).keySet().stream().toList();
    }
}
