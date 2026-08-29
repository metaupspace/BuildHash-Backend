package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.GstinStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    @Convert(converter = com.builddash.backend.infra.persistence.converter.UserPiiStringConverter.class)
    private String phone;

    @Convert(converter = com.builddash.backend.infra.persistence.converter.UserPiiStringConverter.class)
    private String email;

    @Column(name = "google_id")
    @Convert(converter = com.builddash.backend.infra.persistence.converter.UserPiiStringConverter.class)
    private String googleId;

    @Convert(converter = com.builddash.backend.infra.persistence.converter.UserPiiStringConverter.class)
    private String name;

    @Column(name = "business_name")
    @Convert(converter = com.builddash.backend.infra.persistence.converter.UserPiiStringConverter.class)
    private String businessName;

    @Column(name = "gst_number")
    @Convert(converter = com.builddash.backend.infra.persistence.converter.UserPiiStringConverter.class)
    private String gstNumber;

    /** HMAC-SHA256 blind indexes — populated by UserRepositoryAdapter on save, never by domain code. */
    @Column(name = "phone_idx")
    private String phoneIdx;

    @Column(name = "email_idx")
    private String emailIdx;

    @Column(name = "google_id_idx")
    private String googleIdIdx;

    @Enumerated(EnumType.STRING)
    @Column(name = "gstin_status")
    private GstinStatus gstinStatus;
    
    @Column(name = "is_guest")
    private boolean isGuest = false;

    @Column(name = "merged_into_user_id")
    private UUID mergedIntoUserId;

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
