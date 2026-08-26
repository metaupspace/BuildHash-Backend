package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.InvoiceStatus;
import com.builddash.backend.domain.model.Invoice;
import com.builddash.backend.infra.persistence.entity.InvoiceEntity;
import com.builddash.backend.infra.persistence.mapper.InvoiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceMapperTest {

    private InvoiceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new InvoiceMapper();
    }

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String number = "INV-2627-000001";
        String storageKey = "invoices/2026/08/inv-001.pdf";
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Invoice original = new Invoice(
                id,
                orderId,
                number,
                InvoiceStatus.READY,
                storageKey,
                "application/pdf",
                now,
                1,
                now,
                now
        );

        InvoiceEntity entity = mapper.toEntity(original);
        Invoice roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped).isNotNull();
        assertThat(roundTripped.id()).isEqualTo(id);
        assertThat(roundTripped.orderId()).isEqualTo(orderId);
        assertThat(roundTripped.number()).isEqualTo(number);
        assertThat(roundTripped.status()).isEqualTo(InvoiceStatus.READY);
        assertThat(roundTripped.storageKey()).isEqualTo(storageKey);
        assertThat(roundTripped.contentType()).isEqualTo("application/pdf");
        assertThat(roundTripped.generatedAt()).isEqualTo(now);
        assertThat(roundTripped.attemptCount()).isEqualTo(1);
        assertThat(roundTripped.createdAt()).isEqualTo(now);
        assertThat(roundTripped.updatedAt()).isEqualTo(now);
    }

    @Test
    void nullMappings_returnNull() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
