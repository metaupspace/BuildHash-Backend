package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.domain.model.TrendingQueryCount;
import com.builddash.backend.infra.persistence.entity.SearchQueryLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SearchQueryLogJpaRepository extends JpaRepository<SearchQueryLogEntity, UUID> {

    List<SearchQueryLogEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    void deleteByUserId(UUID userId);

    @Query("select new com.builddash.backend.domain.model.TrendingQueryCount(e.queryText, count(e)) "
            + "from SearchQueryLogEntity e where e.createdAt >= :since "
            + "group by e.queryText order by count(e) desc")
    List<TrendingQueryCount> findTrending(Instant since, Pageable pageable);
}
