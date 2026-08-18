package com.builddash.backend.infra.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

/**
 * Builder (concrete class, no interface — deliberate, per PLAN_PHASE1.md Section 4): composes
 * clauses one at a time, only the clauses actually requested end up in the final query. No
 * price-range clause — Section 2 explicitly has no price field in Phase 1, so there's nothing
 * to filter on yet; adding one now would be dead code filtering a field that doesn't exist.
 */
class SearchQueryBuilder {

    private final BoolQuery.Builder bool = new BoolQuery.Builder();
    private String queryText;
    private boolean hasMust = false;

    SearchQueryBuilder matchFuzzy(String text) {
        this.queryText = text;
        if (text != null && !text.isBlank()) {
            bool.must(m -> m.match(t -> t.field("name").query(text).fuzziness("AUTO")));
            hasMust = true;
        }
        return this;
    }

    SearchQueryBuilder autocomplete(String prefix) {
        this.queryText = prefix;
        if (prefix != null && !prefix.isBlank()) {
            bool.must(m -> m.match(t -> t.field("name.autocomplete").query(prefix)));
            hasMust = true;
        }
        return this;
    }

    /** Hindi/English alias lookup — reuses whatever text matchFuzzy/autocomplete already set. */
    SearchQueryBuilder withAlias(String lang) {
        if (queryText != null && !queryText.isBlank()) {
            String field = "hi".equalsIgnoreCase(lang) ? "name.hi" : "nameAliases";
            bool.should(s -> s.match(t -> t.field(field).query(queryText)));
        }
        return this;
    }

    SearchQueryBuilder filterCategory(String category) {
        if (category != null && !category.isBlank()) {
            bool.filter(f -> f.term(t -> t.field("category").value(category)));
        }
        return this;
    }

    Query build() {
        if (!hasMust) {
            bool.must(m -> m.matchAll(ma -> ma));
        }
        return new Query.Builder().bool(bool.build()).build();
    }
}
