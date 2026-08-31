package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.RfqQuoteStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rfq_quotes")
@Getter
@Setter
@NoArgsConstructor
public class RfqQuoteEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "rfq_id", nullable = false)
    private UUID rfqId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RfqQuoteStatus status = RfqQuoteStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
