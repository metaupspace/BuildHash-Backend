package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.ReturnLineItem;
import com.builddash.backend.infra.persistence.entity.ReturnEntity;
import com.builddash.backend.infra.persistence.mapper.ReturnMapper;
import com.builddash.backend.infra.persistence.repository.ReturnJpaRepository;
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
class ReturnRepositoryAdapterTest {

    @Mock
    private ReturnJpaRepository jpaRepository;

    private ReturnMapper mapper;
    private ReturnRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = new ReturnMapper();
        adapter = new ReturnRepositoryAdapter(jpaRepository, mapper);
    }

    private Return sampleDomainReturn() {
        UUID returnId = UUID.randomUUID();
        ReturnLineItem item = new ReturnLineItem(
                UUID.randomUUID(),
                returnId,
                UUID.randomUUID(),
                1,
                new BigDecimal("299.00")
        );
        return new Return(
                returnId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReturnStatus.REQUESTED,
                ReturnReason.DAMAGED,
                List.of("photo.jpg"),
                List.of(item),
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void save_delegatesToJpaAndMapper() {
        Return domain = sampleDomainReturn();
        ReturnEntity entity = mapper.toEntity(domain);
        when(jpaRepository.save(any(ReturnEntity.class))).thenReturn(entity);

        Return saved = adapter.save(domain);

        assertThat(saved).isNotNull();
        assertThat(saved.id()).isEqualTo(domain.id());
        assertThat(saved.status()).isEqualTo(domain.status());
        verify(jpaRepository).save(any(ReturnEntity.class));
    }

    @Test
    void findById_whenFound_returnsMappedDomain() {
        Return domain = sampleDomainReturn();
        ReturnEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findById(domain.id())).thenReturn(Optional.of(entity));

        Optional<Return> result = adapter.findById(domain.id());

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(domain.id());
    }

    @Test
    void findById_whenNotFound_returnsEmpty() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        Optional<Return> result = adapter.findById(id);

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveByOrderId_whenFound_returnsMappedDomain() {
        Return domain = sampleDomainReturn();
        ReturnEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findActiveByOrderId(domain.orderId())).thenReturn(Optional.of(entity));

        Optional<Return> result = adapter.findActiveByOrderId(domain.orderId());

        assertThat(result).isPresent();
        assertThat(result.get().orderId()).isEqualTo(domain.orderId());
    }

    @Test
    void findByOrderId_whenFound_returnsMappedDomain() {
        Return domain = sampleDomainReturn();
        ReturnEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findByOrderId(domain.orderId())).thenReturn(Optional.of(entity));

        Optional<Return> result = adapter.findByOrderId(domain.orderId());

        assertThat(result).isPresent();
        assertThat(result.get().orderId()).isEqualTo(domain.orderId());
    }

    @Test
    void findByOrderId_whenNotFound_returnsEmpty() {
        UUID orderId = UUID.randomUUID();
        when(jpaRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        Optional<Return> result = adapter.findByOrderId(orderId);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByOrderId_returnsMappedDomainList() {
        Return domain = sampleDomainReturn();
        ReturnEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findAllByOrderIdOrderByCreatedAtDesc(domain.orderId())).thenReturn(List.of(entity));

        List<Return> results = adapter.findAllByOrderId(domain.orderId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).orderId()).isEqualTo(domain.orderId());
    }

    @Test
    void findAllByUserId_returnsMappedDomainList() {
        Return domain = sampleDomainReturn();
        ReturnEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findAllByUserIdOrderByCreatedAtDesc(domain.userId())).thenReturn(List.of(entity));

        List<Return> results = adapter.findAllByUserId(domain.userId());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).userId()).isEqualTo(domain.userId());
    }
}
