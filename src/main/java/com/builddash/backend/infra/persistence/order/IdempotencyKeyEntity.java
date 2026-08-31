package com.builddash.backend.infra.persistence.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class IdempotencyKeyEntity {
    
    @Id
    @Column(name = "idempotency_key")
    private String key;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** DB-populated (DEFAULT now()) — read-only for the rolling-window filter (PLAN_PHASE8 decision 10). */
    @Column(name = "created_at", insertable = false, updatable = false)
    private java.time.Instant createdAt;

    public IdempotencyKeyEntity(String key, UUID orderId) {
        this.key = key;
        this.orderId = orderId;
    }
}
