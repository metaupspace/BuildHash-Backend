package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.infra.persistence.entity.InvoiceEntity;
import com.builddash.backend.infra.persistence.mapper.InvoiceMapper;
import com.builddash.backend.infra.persistence.repository.InvoiceJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceRepositoryAdapterTest {

    @Mock
    private InvoiceJpaRepository jpaRepository;

    private InvoiceMapper mapper;
    private InvoiceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = new InvoiceMapper();
        adapter = new InvoiceRepositoryAdapter(jpaRepository, mapper);
    }

    private Invoice sampleInvoice() {
        return new Invoice(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "INV-2627-000001",
                InvoiceStatus.READY,
                "invoices/test.pdf",
                "application/pdf",
                Instant.now(),
                1,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void save_delegatesToJpaAndMapper() {
        Invoice domain = sampleInvoice();
        InvoiceEntity entity = mapper.toEntity(domain);
        when(jpaRepository.save(any(InvoiceEntity.class))).thenReturn(entity);

        Invoice saved = adapter.save(domain);

        assertThat(saved).isNotNull();
        assertThat(saved.id()).isEqualTo(domain.id());
        assertThat(saved.number()).isEqualTo(domain.number());
        verify(jpaRepository).save(any(InvoiceEntity.class));
    }

    @Test
    void findById_whenFound_returnsMappedDomain() {
        Invoice domain = sampleInvoice();
        InvoiceEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findById(domain.id())).thenReturn(Optional.of(entity));

        Optional<Invoice> result = adapter.findById(domain.id());

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(domain.id());
    }

    @Test
    void findByIdForUpdate_whenFound_returnsMappedDomain() {
        Invoice domain = sampleInvoice();
        InvoiceEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findByIdForUpdate(domain.id())).thenReturn(Optional.of(entity));

        Optional<Invoice> result = adapter.findByIdForUpdate(domain.id());

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(domain.id());
    }

    @Test
    void findByOrderId_whenFound_returnsMappedDomain() {
        Invoice domain = sampleInvoice();
        InvoiceEntity entity = mapper.toEntity(domain);
        when(jpaRepository.findByOrderId(domain.orderId())).thenReturn(Optional.of(entity));

        Optional<Invoice> result = adapter.findByOrderId(domain.orderId());

        assertThat(result).isPresent();
        assertThat(result.get().orderId()).isEqualTo(domain.orderId());
    }

    @Test
    void findSchedulerClaimableInvoices_delegatesToJpa() {
        Invoice domain = sampleInvoice();
        InvoiceEntity entity = mapper.toEntity(domain);
        Instant cutoff = Instant.now().minusSeconds(900);
        when(jpaRepository.findSchedulerClaimableInvoices(3, cutoff)).thenReturn(List.of(entity));

        List<Invoice> results = adapter.findSchedulerClaimableInvoices(3, cutoff);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(domain.id());
    }

    @Test
    void findDlqClaimableInvoices_delegatesToJpa() {
        Invoice domain = sampleInvoice();
        InvoiceEntity entity = mapper.toEntity(domain);
        Instant cutoff = Instant.now().minusSeconds(900);
        when(jpaRepository.findDlqClaimableInvoices(3, cutoff)).thenReturn(List.of(entity));

        List<Invoice> results = adapter.findDlqClaimableInvoices(3, cutoff);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(domain.id());
    }
}
