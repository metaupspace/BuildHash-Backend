package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Coupon;
import com.builddash.backend.domain.port.CouponRepository;
import com.builddash.backend.infra.persistence.mapper.CouponMapper;
import com.builddash.backend.infra.persistence.repository.CouponJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class CouponRepositoryAdapter implements CouponRepository {

    private final CouponJpaRepository jpaRepository;

    CouponRepositoryAdapter(CouponJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
        return jpaRepository.findByCodeIgnoreCase(code).map(CouponMapper::toDomain);
    }
}
