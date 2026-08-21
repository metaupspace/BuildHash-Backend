package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.Coupon;

import java.util.Optional;

public interface CouponRepository {

    Optional<Coupon> findByCode(String code);
}
