package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.ProductSearchHit;

import java.util.List;

/**
 * Read path against the search index — separate from SearchIndex (write path) and
 * SearchIndexAdmin (index lifecycle). All ES query-building (SearchQueryBuilder) stays
 * inside the adapter; this signature is framework-free.
 *
 * category is a NAME (e.g. "Cement"), not a UUID — Section 2's ES mapping only indexes
 * ProductSyncPayload.category (the category's name/keyword), never categoryId. Unlike
 * GET /products?category=, which filters Postgres by categoryId, this filters by the same
 * string that's actually in the index. A genuine, plan-inherited inconsistency between the
 * two endpoints' category filter shape, not an oversight.
 */
public interface SearchQueryGateway {

    List<ProductSearchHit> search(String query, String lang, String category, int limit);

    List<String> suggest(String prefix, String lang, int limit);
}
