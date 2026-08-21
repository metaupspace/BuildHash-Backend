package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponJpaRepository extends JpaRepository<CouponEntity, UUID> {

    Optional<CouponEntity> findByCodeIgnoreCase(String code);
}
