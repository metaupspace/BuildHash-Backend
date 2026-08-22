package com.builddash.backend.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.builddash.backend.domain.model.ProductSearchHit;
import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.SearchQueryGateway;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class ElasticsearchSearchQueryGatewayAdapter implements SearchQueryGateway {

    private static final String PRODUCTS_ALIAS = "products";

    private final ElasticsearchClient client;


    @Override
    public List<ProductSearchHit> search(String query, String lang, String category, int limit) {
        Query esQuery = new SearchQueryBuilder().matchFuzzy(query).withAlias(lang).filterCategory(category).build();
        return execute(esQuery, limit).stream()
                .map(p -> new ProductSearchHit(p.productId(), p.name(), p.category(), p.brand(), p.stockStatus()))
                .toList();
    }

    @Override
    public List<String> suggest(String prefix, String lang, int limit) {
        Query esQuery = new SearchQueryBuilder().autocomplete(prefix).withAlias(lang).build();
        return execute(esQuery, limit).stream()
                .map(ProductSyncPayload::name)
                .distinct()
                .toList();
    }

    private List<ProductSyncPayload> execute(Query query, int limit) {
        try {
            SearchResponse<ProductSyncPayload> response = client.search(s -> s
                    .index(PRODUCTS_ALIAS)
                    .query(query)
                    .size(limit), ProductSyncPayload.class);
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Search execution failed", e);
        }
    }
}
