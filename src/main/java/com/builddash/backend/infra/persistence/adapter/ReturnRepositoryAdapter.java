package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.infra.persistence.mapper.ReturnMapper;
import com.builddash.backend.infra.persistence.repository.ReturnJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class ReturnRepositoryAdapter implements ReturnRepository {

    private final ReturnJpaRepository jpaRepository;
    private final ReturnMapper mapper;

    @Override
    public Return save(Return returnAggregate) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(returnAggregate)));
    }

    @Override
    public Optional<Return> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Return> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    public List<Return> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
