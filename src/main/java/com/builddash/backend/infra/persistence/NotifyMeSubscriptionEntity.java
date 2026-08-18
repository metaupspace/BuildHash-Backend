package com.builddash.backend.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notify_me_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class NotifyMeSubscriptionEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
