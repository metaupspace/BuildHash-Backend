package com.builddash.backend.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "rfq_items")
@Getter
@Setter
@NoArgsConstructor
public class RfqItemEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "rfq_id", nullable = false)
    private UUID rfqId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;
}
