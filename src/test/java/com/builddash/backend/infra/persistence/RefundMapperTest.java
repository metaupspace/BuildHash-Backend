package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.RefundStatus;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.infra.persistence.entity.RefundEntity;
import com.builddash.backend.infra.persistence.mapper.RefundMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundMapperTest {

    private RefundMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new RefundMapper();
    }

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        UUID id = UUID.randomUUID();
        UUID returnId = UUID.randomUUID();
        String txId = "tx_12345";
        BigDecimal amount = new BigDecimal("499.99");
        String gatewayRefundId = "ref_gateway_999";
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Refund original = new Refund(
                id,
                returnId,
                txId,
                amount,
                RefundStatus.SUCCESS,
                gatewayRefundId,
                now,
                now
        );

        RefundEntity entity = mapper.toEntity(original);
        Refund roundTripped = mapper.toDomain(entity);

        assertThat(roundTripped).isNotNull();
        assertThat(roundTripped.id()).isEqualTo(id);
        assertThat(roundTripped.returnId()).isEqualTo(returnId);
        assertThat(roundTripped.paymentTransactionId()).isEqualTo(txId);
        assertThat(roundTripped.amount()).isEqualByComparingTo(amount);
        assertThat(roundTripped.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(roundTripped.gatewayRefundId()).isEqualTo(gatewayRefundId);
        assertThat(roundTripped.createdAt()).isEqualTo(now);
        assertThat(roundTripped.updatedAt()).isEqualTo(now);
    }

    @Test
    void nullMappings_returnNull() {
        assertThat(mapper.toEntity(null)).isNull();
        assertThat(mapper.toDomain(null)).isNull();
    }
}
