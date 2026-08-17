package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.HsnGstRate;

import java.util.Optional;

public interface HsnGstRateRepository {

    Optional<HsnGstRate> findByHsnCode(String hsnCode);
}
