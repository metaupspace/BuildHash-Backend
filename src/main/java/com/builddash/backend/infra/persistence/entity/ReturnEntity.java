package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "returns")
@Getter
@Setter
@NoArgsConstructor
public class ReturnEntity {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnReason reason;

    @Column(name = "photo_keys", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> photoKeys = new ArrayList<>();

    @OneToMany(mappedBy = "returnEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<ReturnLineItemEntity> lineItems = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addLineItem(ReturnLineItemEntity item) {
        lineItems.add(item);
        item.setReturnEntity(this);
    }
}
