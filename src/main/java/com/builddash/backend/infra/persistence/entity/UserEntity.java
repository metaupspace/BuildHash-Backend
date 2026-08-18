package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.GstinStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    @Id
    @UuidGenerator
    private UUID id;

    private String phone;
    private String email;

    @Column(name = "google_id")
    private String googleId;

    private String name;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "gst_number")
    private String gstNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "gstin_status")
    private GstinStatus gstinStatus;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
