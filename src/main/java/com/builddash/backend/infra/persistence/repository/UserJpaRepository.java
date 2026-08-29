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
}
