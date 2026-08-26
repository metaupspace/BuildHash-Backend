package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.infra.persistence.mapper.RefundMapper;
import com.builddash.backend.infra.persistence.repository.RefundJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class RefundRepositoryAdapter implements RefundRepository {

    private final RefundJpaRepository jpaRepository;
    private final RefundMapper mapper;

    @Override
    public Refund save(Refund refund) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(refund)));
    }

    @Override
    public Optional<Refund> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Refund> findByReturnId(UUID returnId) {
        return jpaRepository.findByReturnId(returnId).map(mapper::toDomain);
    }

    @Override
    public Optional<Refund> findByGatewayRefundId(String gatewayRefundId) {
        return jpaRepository.findByGatewayRefundId(gatewayRefundId).map(mapper::toDomain);
    }

    @Override
    public List<Refund> findAllByReturnId(UUID returnId) {
        return jpaRepository.findAllByReturnIdOrderByCreatedAtDesc(returnId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
