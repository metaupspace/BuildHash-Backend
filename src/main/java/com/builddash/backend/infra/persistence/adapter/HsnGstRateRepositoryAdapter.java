package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.HsnGstRate;
import com.builddash.backend.domain.port.HsnGstRateRepository;
import com.builddash.backend.infra.persistence.mapper.HsnGstRateMapper;
import com.builddash.backend.infra.persistence.repository.HsnGstRateJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class HsnGstRateRepositoryAdapter implements HsnGstRateRepository {

    private final HsnGstRateJpaRepository jpaRepository;


    @Override
    public Optional<HsnGstRate> findByHsnCode(String hsnCode) {
        return jpaRepository.findById(hsnCode).map(HsnGstRateMapper::toDomain);
    }
}
