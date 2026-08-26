package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.infra.persistence.entity.RefundEntity;
import com.builddash.backend.infra.persistence.mapper.RefundMapper;
import com.builddash.backend.infra.persistence.repository.RefundJpaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundRepositoryAdapterTest {

    @Mock
    private RefundJpaRepository jpaRepository;

    private RefundMapper mapper;
    private RefundRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = new RefundMapper();
        adapter = new RefundRepositoryAdapter(jpaRepository, mapper);
    }

    private Refund sampleDomainRefund() {
        return new Refund(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tx_abc123",
                new BigDecimal("350.00"),
                RefundStatus.PENDING,
                "dummy_ref_xyz",
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void save_delegatesToJpaAndMapper() {
        Refund domain = sampleDomainRefund();
        RefundEntity entity = mapper.toEntity(domain);
        when(jpaRepository.save(any(RefundEntity.class))).thenReturn(entity);

        Refund saved = adapter.save(domain);

        assertThat(saved).isNotNull();
        assertThat(saved.id()).isEqualTo(domain.id());
        assertThat(saved.amount()).isEqualByComparingTo(domain.amount());
        verify(jpaRepository).save(any(RefundEntity.class));
    }

    @Test
    void findById_whenFound_returnsMappedDomain() {
        Refund domain = sampleDomainRefund();
        RefundEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findById(domain.id())).thenReturn(Optional.of(entity));

        Optional<Refund> result = adapter.findById(domain.id());

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(domain.id());
    }

    @Test
    void findByReturnId_whenFound_returnsMappedDomain() {
        Refund domain = sampleDomainRefund();
        RefundEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findByReturnId(domain.returnId())).thenReturn(Optional.of(entity));

        Optional<Refund> result = adapter.findByReturnId(domain.returnId());

        assertThat(result).isPresent();
        assertThat(result.get().returnId()).isEqualTo(domain.returnId());
    }

    @Test
    void findByGatewayRefundId_whenFound_returnsMappedDomain() {
        Refund domain = sampleDomainRefund();
        RefundEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findByGatewayRefundId(domain.gatewayRefundId())).thenReturn(Optional.of(entity));

        Optional<Refund> result = adapter.findByGatewayRefundId(domain.gatewayRefundId());

        assertThat(result).isPresent();
        assertThat(result.get().gatewayRefundId()).isEqualTo(domain.gatewayRefundId());
    }

    @Test
    void findAllByReturnId_returnsMappedDomainList() {
        Refund domain = sampleDomainRefund();
        RefundEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findAllByReturnIdOrderByCreatedAtDesc(domain.returnId())).thenReturn(List.of(entity));

        List<Refund> results = adapter.findAllByReturnId(domain.returnId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).returnId()).isEqualTo(domain.returnId());
    }
}
