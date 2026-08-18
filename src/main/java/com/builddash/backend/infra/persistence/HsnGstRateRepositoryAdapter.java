package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.HsnGstRate;
import com.builddash.backend.domain.port.HsnGstRateRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class HsnGstRateRepositoryAdapter implements HsnGstRateRepository {

    private final HsnGstRateJpaRepository jpaRepository;

    HsnGstRateRepositoryAdapter(HsnGstRateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<HsnGstRate> findByHsnCode(String hsnCode) {
        return jpaRepository.findById(hsnCode).map(HsnGstRateMapper::toDomain);
    }
}
