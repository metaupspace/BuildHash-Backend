package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.ProductSyncPayload;

/**
 * Index-lifecycle concern (blue-green reindex), separate from SearchIndex (live-sync, per
 * document, alias-targeted, external-versioned) — different caller (CatalogReindexer vs. the
 * catalog.product.changed listener), different responsibility, same ISP precedent as every
 * other port split in this codebase.
 */
public interface SearchIndexAdmin {

    /** Creates a new versioned index (e.g. products_v{timestamp}) and returns its name. */
    String createIndex();

    /** Writes into a specific index by name — never the alias. Used only during backfill. */
    void indexDocument(String indexName, ProductSyncPayload payload);

    /** Atomically repoints alias to newIndexName (remove old + add new in one call). */
    void swapAlias(String alias, String newIndexName);

    /** The index an alias currently resolves to, or null if the alias doesn't exist yet. */
    String resolveAlias(String alias);
}
