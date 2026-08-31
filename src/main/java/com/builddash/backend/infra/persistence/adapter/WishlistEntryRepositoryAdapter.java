package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.WishlistEntry;
import com.builddash.backend.domain.port.WishlistRepository;
import com.builddash.backend.infra.persistence.mapper.WishlistEntryMapper;
import com.builddash.backend.infra.persistence.repository.WishlistEntryJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class WishlistEntryRepositoryAdapter implements WishlistRepository {

    private final WishlistEntryJpaRepository jpaRepository;


    @Override
    public WishlistEntry save(WishlistEntry entry) {
        return WishlistEntryMapper.toDomain(jpaRepository.save(WishlistEntryMapper.toEntity(entry)));
    }

    @Override
    public List<WishlistEntry> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(WishlistEntryMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<WishlistEntry> findByUserIdAndProductId(UUID userId, UUID productId) {
        return jpaRepository.findByUserIdAndProductId(userId, productId).map(WishlistEntryMapper::toDomain);
    }

    @Override
    @Transactional
    public void deleteByUserIdAndProductId(UUID userId, UUID productId) {
        jpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }
}
