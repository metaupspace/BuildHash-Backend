package com.builddash.backend.infra.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryBuilderTest {

    @Test
    void build_withOnlyFuzzyText_addsOnlyMustClause() {
        Query query = new SearchQueryBuilder().matchFuzzy("cement").build();

        assertThat(query.bool().must()).hasSize(1);
        assertThat(query.bool().should()).isEmpty();
        assertThat(query.bool().filter()).isEmpty();
    }

    @Test
    void build_withNoTextAtAll_fallsBackToMatchAll() {
        Query query = new SearchQueryBuilder().build();

        assertThat(query.bool().must()).hasSize(1);
        assertThat(query.bool().must().get(0).isMatchAll()).isTrue();
    }

    @Test
    void build_withAliasLang_addsShouldClauseOnlyWhenTextPresent() {
        Query withText = new SearchQueryBuilder().matchFuzzy("cement").withAlias("hi").build();
        assertThat(withText.bool().should()).hasSize(1);

        Query withoutText = new SearchQueryBuilder().withAlias("hi").build();
        assertThat(withoutText.bool().should()).isEmpty();
    }

    @Test
    void build_withCategory_addsFilterClause() {
        Query query = new SearchQueryBuilder().filterCategory("Cement").build();

        assertThat(query.bool().filter()).hasSize(1);
    }

    @Test
    void build_withNoCategory_addsNoFilterClause() {
        Query query = new SearchQueryBuilder().matchFuzzy("cement").filterCategory(null).build();

        assertThat(query.bool().filter()).isEmpty();
    }
}
