package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.MarginRule;
import com.builddash.backend.domain.port.MarginRuleRepository;
import com.builddash.backend.infra.persistence.mapper.MarginRuleMapper;
import com.builddash.backend.infra.persistence.repository.MarginRuleJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class MarginRuleRepositoryAdapter implements MarginRuleRepository {

    private final MarginRuleJpaRepository jpaRepository;


    @Override
    public Optional<MarginRule> findByProductId(UUID productId) {
        return jpaRepository.findByProductId(productId).map(MarginRuleMapper::toDomain);
    }

    @Override
    public Optional<MarginRule> findByCategoryId(UUID categoryId) {
        return jpaRepository.findByCategoryId(categoryId).map(MarginRuleMapper::toDomain);
    }
}
