package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.SearchQueryLogEntry;
import com.builddash.backend.domain.model.TrendingQueryCount;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SearchQueryLogRepository {

    SearchQueryLogEntry save(SearchQueryLogEntry entry);

    List<SearchQueryLogEntry> findByUserId(UUID userId, int limit);

    void deleteByUserId(UUID userId);

    /** Aggregate across all users (including anonymous) — trending reflects all traffic. */
    List<TrendingQueryCount> findTrending(Instant since, int limit);
}
