package com.builddash.backend.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class AddressEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    @Convert(converter = com.builddash.backend.infra.persistence.converter.AddressPiiStringConverter.class)
    private String line1;

    @Convert(converter = com.builddash.backend.infra.persistence.converter.AddressPiiStringConverter.class)
    private String line2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(name = "zip_code", nullable = false)
    private String zipCode;

    @Convert(converter = com.builddash.backend.infra.persistence.converter.AddressPiiDoubleConverter.class)
    private Double lat;

    @Convert(converter = com.builddash.backend.infra.persistence.converter.AddressPiiDoubleConverter.class)
    private Double lng;

    @Column(name = "is_serviceable", nullable = false)
    private boolean isServiceable;

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
