package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.exception.ContractPriceOverlapException;
import com.builddash.backend.domain.model.ContractPrice;
import com.builddash.backend.infra.persistence.entity.ContractPriceEntity;
import com.builddash.backend.infra.persistence.repository.ContractPriceJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * findActive's active/expired/not-yet-effective resolution is plain Java filtering over an
 * already-fetched list — genuinely a Mockito-unit-testable concern, no DB needed. The DB-level
 * exclusion-constraint backstop (excl_contract_pricing_no_overlap) is proven separately by
 * ContractPriceOverlapJpaIT, which needs a real Postgres.
 */
@ExtendWith(MockitoExtension.class)
class ContractPriceRepositoryAdapterTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    @Mock
    private ContractPriceJpaRepository jpaRepository;

    private ContractPriceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ContractPriceRepositoryAdapter(jpaRepository);
    }

    private static ContractPriceEntity entity(Instant from, Instant to, BigDecimal unitPrice) {
        ContractPriceEntity entity = new ContractPriceEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(USER_ID);
        entity.setProductId(PRODUCT_ID);
        entity.setUnitPrice(unitPrice);
        entity.setEffectiveFrom(from);
        entity.setEffectiveTo(to);
        return entity;
    }

    @Test
    void findActive_windowCoversNow_returnsIt() {
        ContractPriceEntity active = entity(NOW.minusSeconds(3600), NOW.plusSeconds(3600), new BigDecimal("50.00"));
        when(jpaRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(List.of(active));

        Optional<ContractPrice> result = adapter.findActive(USER_ID, PRODUCT_ID, NOW);

        assertThat(result).isPresent();
        assertThat(result.get().getUnitPrice()).isEqualByComparingTo("50.00");
    }

    @Test
    void findActive_expired_notReturned() {
        ContractPriceEntity expired = entity(NOW.minusSeconds(7200), NOW.minusSeconds(3600), new BigDecimal("50.00"));
        when(jpaRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(List.of(expired));

        assertThat(adapter.findActive(USER_ID, PRODUCT_ID, NOW)).isEmpty();
    }

    @Test
    void findActive_notYetEffective_notReturned() {
        ContractPriceEntity future = entity(NOW.plusSeconds(3600), null, new BigDecimal("50.00"));
        when(jpaRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(List.of(future));

        assertThat(adapter.findActive(USER_ID, PRODUCT_ID, NOW)).isEmpty();
    }

    @Test
    void findActive_openEndedWindow_treatsNullEffectiveToAsUnbounded() {
        ContractPriceEntity openEnded = entity(NOW.minusSeconds(3600), null, new BigDecimal("50.00"));
        when(jpaRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(List.of(openEnded));

        assertThat(adapter.findActive(USER_ID, PRODUCT_ID, NOW)).isPresent();
    }

    @Test
    void save_overlappingWindow_rejectedBeforeHittingTheDatabase() {
        ContractPriceEntity existing = entity(NOW.minusSeconds(3600), NOW.plusSeconds(3600), new BigDecimal("50.00"));
        when(jpaRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(List.of(existing));

        ContractPrice overlapping = new ContractPrice(null, USER_ID, PRODUCT_ID, new BigDecimal("60.00"),
                NOW, NOW.plusSeconds(7200), null, null);

        assertThatThrownBy(() -> adapter.save(overlapping))
                .isInstanceOf(ContractPriceOverlapException.class);
    }

    @Test
    void save_nonOverlappingWindow_delegatesToJpaRepository() {
        ContractPriceEntity existing = entity(NOW.minusSeconds(7200), NOW.minusSeconds(3600), new BigDecimal("50.00"));
        when(jpaRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(List.of(existing));
        ContractPrice fresh = new ContractPrice(null, USER_ID, PRODUCT_ID, new BigDecimal("60.00"),
                NOW, null, null, null);
        ContractPriceEntity saved = entity(NOW, null, new BigDecimal("60.00"));
        when(jpaRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(ContractPriceEntity.class))).thenReturn(saved);

        ContractPrice result = adapter.save(fresh);

        assertThat(result.getUnitPrice()).isEqualByComparingTo("60.00");
    }
}
