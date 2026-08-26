package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.ReturnLineItem;
import com.builddash.backend.infra.persistence.entity.ReturnEntity;
import com.builddash.backend.infra.persistence.mapper.ReturnMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnMapperTest {

    private ReturnMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ReturnMapper();
    }

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID lineItemId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        ReturnLineItem lineItem = new ReturnLineItem(
                lineItemId,
                returnId,
                productId,
                3,
                new BigDecimal("1299.50")
        );

        Return original = new Return(
                returnId,
                orderId,
                userId,
                ReturnStatus.APPROVED,
                ReturnReason.DEFECTIVE,
                List.of("returns/photos/p1.jpg", "returns/photos/p2.jpg"),
                List.of(lineItem),
                now,
                now
        );

        ReturnEntity entity = mapper.toEntity(original);
        Return roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped).isNotNull();
        assertThat(roundTripped.id()).isEqualTo(original.id());
        assertThat(roundTripped.orderId()).isEqualTo(original.orderId());
        assertThat(roundTripped.userId()).isEqualTo(original.userId());
        assertThat(roundTripped.status()).isEqualTo(original.status());
        assertThat(roundTripped.reason()).isEqualTo(original.reason());
        assertThat(roundTripped.photoKeys()).containsExactlyElementsOf(original.photoKeys());
        assertThat(roundTripped.createdAt()).isEqualTo(original.createdAt());
        assertThat(roundTripped.updatedAt()).isEqualTo(original.updatedAt());

        assertThat(roundTripped.lineItems()).hasSize(1);
        ReturnLineItem roundTrippedItem = roundTripped.lineItems().get(0);
        assertThat(roundTrippedItem.id()).isEqualTo(lineItemId);
        assertThat(roundTrippedItem.returnId()).isEqualTo(returnId);
        assertThat(roundTrippedItem.productId()).isEqualTo(productId);
        assertThat(roundTrippedItem.quantityRequested()).isEqualTo(3);
        assertThat(roundTrippedItem.refundAmount()).isEqualByComparingTo("1299.50");
    }

    @Test
    void nullMappings_returnNull() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
        assertThat(mapper.toLineItemDomain(null)).isNull();
    }

    @Test
    void emptyCollections_handledGracefully() {
        Return returnObj = new Return(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReturnStatus.REQUESTED,
                ReturnReason.DAMAGED,
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        ReturnEntity entity = mapper.toEntity(returnObj);
        assertThat(entity.getPhotoKeys()).isEmpty();
        assertThat(entity.getLineItems()).isEmpty();

        Return domain = mapper.toDomain(entity);
        assertThat(domain.photoKeys()).isEmpty();
        assertThat(domain.lineItems()).isEmpty();
    }
}
