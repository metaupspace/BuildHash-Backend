package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.SearchQueryLogEntry;
import com.builddash.backend.domain.model.TrendingQueryCount;
import com.builddash.backend.domain.port.SearchQueryLogRepository;
import com.builddash.backend.infra.persistence.mapper.SearchQueryLogMapper;
import com.builddash.backend.infra.persistence.repository.SearchQueryLogJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class SearchQueryLogRepositoryAdapter implements SearchQueryLogRepository {

    private final SearchQueryLogJpaRepository jpaRepository;

    SearchQueryLogRepositoryAdapter(SearchQueryLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SearchQueryLogEntry save(SearchQueryLogEntry entry) {
        return SearchQueryLogMapper.toDomain(jpaRepository.save(SearchQueryLogMapper.toEntity(entry)));
    }

    @Override
    public List<SearchQueryLogEntry> findByUserId(UUID userId, int limit) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit)).stream()
                .map(SearchQueryLogMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }

    @Override
    public List<TrendingQueryCount> findTrending(Instant since, int limit) {
        return jpaRepository.findTrending(since, PageRequest.of(0, limit));
    }
}
