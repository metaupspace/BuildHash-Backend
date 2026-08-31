package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.CartType;
import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B2B_DRAFT boundary audit on real Postgres: the new cart type persists with its
 // company scope and is invisible to every PRIMARY-scoped lookup (primary cart fetch,
 // stale/abandonment sweep). V14 uniqueness: one B2B_DRAFT per (user, project, type);
 * later checkpoints give concurrent drafts distinct source ids in project_id.
 */
class CartB2bDraftJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'Acme')", companyId);
    }

    private Cart save(Cart cart) {
        return cartRepository.save(cart);
    }

    @Test
    void b2bDraftPersists_companyIdRoundTrips() {
        Cart draft = save(new Cart(UUID.randomUUID(), userId, null, CartType.B2B_DRAFT, null,
                java.util.List.of(), companyId));

        Cart loaded = cartRepository.findById(draft.id()).orElseThrow();
        assertThat(loaded.type()).isEqualTo(CartType.B2B_DRAFT);
        assertThat(loaded.companyId()).isEqualTo(companyId);
    }

    @Test
    void b2cPrimaryCart_persistsWithNullCompany() {
        Cart primary = save(new Cart(UUID.randomUUID(), userId, null, CartType.PRIMARY, null,
                java.util.List.of(), null));

        Cart loaded = cartRepository.findById(primary.id()).orElseThrow();
        assertThat(loaded.companyId()).isNull();
    }

    @Test
    void primaryLookup_excludesB2bDraft_evenWithoutAPrimaryPresent() {
        Cart draft = save(new Cart(UUID.randomUUID(), userId, null, CartType.B2B_DRAFT, null,
                java.util.List.of(), companyId));

        // findByUserIdAndProjectId is PRIMARY-only: the draft must not leak into it
        assertThat(cartRepository.findByUserIdAndProjectId(userId, null)).isEmpty();
        assertThat(draft.type()).isEqualTo(CartType.B2B_DRAFT);
    }

    @Test
    void primaryLookup_returnsPrimaryAlongsideDraft() {
        Cart primary = save(new Cart(UUID.randomUUID(), userId, null, CartType.PRIMARY, null,
                java.util.List.of(), null));
        save(new Cart(UUID.randomUUID(), userId, null, CartType.B2B_DRAFT, null,
                java.util.List.of(), companyId));

        Cart found = cartRepository.findByUserIdAndProjectId(userId, null).orElseThrow();
        assertThat(found.id()).isEqualTo(primary.id());
        assertThat(found.type()).isEqualTo(CartType.PRIMARY);
    }

    @Test
    void abandonmentSweepScope_primaryOnly_neverTouchesDrafts() {
        // Old draft (well past any cutoff) + old PRIMARY carrying an item
        UUID draftId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO carts (id, user_id, cart_type, company_id, created_at, updated_at) "
                        + "VALUES (?, ?, 'B2B_DRAFT', ?, now() - interval '30 days', now() - interval '30 days')",
                draftId, userId, companyId);
        UUID primaryId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug) VALUES (?, 'C', ?)", categoryId, "c" + categoryId);
        UUID productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) "
                        + "VALUES (?, 'P', ?, ?, 'ACTIVE', '2523', now(), now())", productId, "p" + productId, categoryId);
        jdbcTemplate.update("INSERT INTO carts (id, user_id, cart_type, created_at, updated_at) "
                        + "VALUES (?, ?, 'PRIMARY', now() - interval '30 days', now() - interval '30 days')",
                primaryId, userId);
        jdbcTemplate.update("INSERT INTO cart_line_items (id, cart_id, product_id, quantity) VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), primaryId, productId);

        // findStalePrimaryCarts is the abandonment job's query — PRIMARY only
        var stale = cartRepository.findStalePrimaryCarts(Instant.now().minusSeconds(60));
        assertThat(stale).isNotEmpty(); // the old PRIMARY IS picked up
        assertThat(stale).noneMatch(c -> c.id().equals(draftId)); // the draft never is
        assertThat(stale).allMatch(c -> c.type() == CartType.PRIMARY);
    }
}
