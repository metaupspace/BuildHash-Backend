package com.builddash.backend.infra.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch._types.analysis.TokenChar;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.SearchIndexAdmin;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Index/alias lifecycle for the blue-green reindex (PLAN_PHASE1.md Section 3). createIndex()
 * builds the full Section-2 mapping: name gets a standard analyzer, an edge-ngram autocomplete
 * sub-field, and a Hindi-analyzer sub-field; category/brand/stockStatus are keywords;
 * attributes is flattened (schema-free, mirrors the Postgres JSONB shape without an ES
 * mapping change per category).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ElasticsearchSearchIndexAdminAdapter implements SearchIndexAdmin {

    private final ElasticsearchClient client;


    @Override
    public String createIndex() {
        String indexName = "products_v" + System.currentTimeMillis();
        try {
            client.indices().create(c -> c
                    .index(indexName)
                    .settings(s -> s.analysis(a -> a
                            .tokenizer("autocomplete_tokenizer", t -> t
                                    .definition(d -> d.edgeNgram(e -> e.minGram(2).maxGram(15)
                                            .tokenChars(TokenChar.Letter, TokenChar.Digit))))
                            .analyzer("autocomplete_analyzer", an -> an
                                    .custom(ca -> ca.tokenizer("autocomplete_tokenizer").filter("lowercase")))))
                    .mappings(m -> m
                            .properties("productId", p -> p.keyword(k -> k))
                            .properties("name", p -> p.text(t -> t
                                    .fields("autocomplete", f -> f.text(ft -> ft.analyzer("autocomplete_analyzer")))
                                    .fields("hi", f -> f.text(ft -> ft.analyzer("hindi")))))
                            .properties("nameAliases", p -> p.text(t -> t))
                            .properties("category", p -> p.keyword(k -> k.fields("text", f -> f.text(t -> t))))
                            .properties("brand", p -> p.keyword(k -> k))
                            .properties("attributes", p -> p.flattened(f -> f))
                            .properties("stockStatus", p -> p.keyword(k -> k))
                            .properties("updatedAt", p -> p.date(d -> d))));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create index " + indexName, e);
        }
        return indexName;
    }

    @Override
    public void indexDocument(String indexName, ProductSyncPayload payload) {
        try {
            client.index(i -> i
                    .index(indexName)
                    .id(payload.productId().toString())
                    .document(payload)
                    .versionType(VersionType.External)
                    .version(payload.updatedAtEpochMillis()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to index product " + payload.productId()
                    + " into " + indexName, e);
        }
    }

    @Override
    public void indexDocumentsBulk(String indexName, java.util.List<ProductSyncPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }
        try {
            client.bulk(b -> {
                b.index(indexName);
                for (ProductSyncPayload payload : payloads) {
                    b.operations(op -> op
                            .index(idx -> idx
                                    .index(indexName)
                                    .id(payload.productId().toString())
                                    .document(payload)
                                    .versionType(VersionType.External)
                                    .version(payload.updatedAtEpochMillis())
                            )
                    );
                }
                return b;
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to bulk index " + payloads.size() + " products into " + indexName, e);
        }
    }

    @Override
    public void swapAlias(String alias, String newIndexName) {
        String currentIndex = resolveAlias(alias);

        // A concrete index named after the alias blocks alias creation — delete it.
        if (currentIndex == null) {
            deleteConcreteIndexIfExists(alias);
        }

        try {
            client.indices().updateAliases(u -> {
                if (currentIndex != null) {
                    u.actions(a -> a.remove(r -> r.index(currentIndex).alias(alias)));
                }
                return u.actions(a -> a.add(ad -> ad.index(newIndexName).alias(alias)));
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to swap alias " + alias + " to " + newIndexName, e);
        }
    }

    @Override
    public void deleteIndex(String indexName) {
        try {
            client.indices().delete(d -> d.index(indexName));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete index " + indexName, e);
        }
    }

    private void deleteConcreteIndexIfExists(String name) {
        try {
            boolean exists = client.indices().exists(e -> e.index(name)).value();
            if (exists) {
                client.indices().delete(d -> d.index(name));
                log.info("Deleted concrete index '{}' blocking alias creation", name);
            }
        } catch (IOException e) {
            log.warn("Failed to check/delete concrete index '{}': {}", name, e.getMessage());
        }
    }

    @Override
    public String resolveAlias(String alias) {
        try {
            GetAliasResponse response = client.indices().getAlias(g -> g.name(alias));
            Map<String, ?> result = response.result();
            return result.keySet().stream().findFirst().orElse(null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to resolve alias " + alias, e);
        } catch (co.elastic.clients.elasticsearch._types.ElasticsearchException e) {
            return null;
        }
    }
}
