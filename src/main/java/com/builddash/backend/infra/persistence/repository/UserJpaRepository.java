package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    /** Blind-index lookups (PLAN_PHASE8 decision 3): callers pass the HMAC idx value, never plaintext. */
    Optional<UserEntity> findByPhoneIdx(String phoneIdx);

    Optional<UserEntity> findByEmailIdx(String emailIdx);

    Optional<UserEntity> findByGoogleIdIdx(String googleIdIdx);

    @Query("select u.phone from UserEntity u where u.phone is not null")
    List<String> findAllPhones();

    /**
     * DPDP tombstone: single UPDATE nulling identity + idx columns. NULL bypasses the
     * encryption converters entirely (no null round-trip) and kills the blind indexes in the
     * same statement. Idempotent — re-running on a tombstoned row is harmless, so the
     * guest-merged case (already tombstoned by merge) needs no explicit guard.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update UserEntity u set u.phone = null, u.email = null, u.name = null, " +
            "u.businessName = null, u.gstNumber = null, u.googleId = null, " +
            "u.phoneIdx = null, u.emailIdx = null, u.googleIdIdx = null " +
            "where u.id = :id")
    void anonymizeById(@org.springframework.lang.NonNull UUID id);
}
