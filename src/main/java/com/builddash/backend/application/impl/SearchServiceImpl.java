package com.builddash.backend.application.impl;

import com.builddash.backend.domain.model.ProductSearchHit;
import com.builddash.backend.domain.model.SearchQueryLogEntry;
import com.builddash.backend.domain.model.TrendingQueryCount;
import com.builddash.backend.domain.port.ImageSearchProvider;
import com.builddash.backend.domain.port.SearchQueryGateway;
import com.builddash.backend.domain.port.SearchQueryLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * No separate interface — single controller caller, one cohesive workflow, same judgment as
 * OtpSendService/CatalogOutboxRelay. Redis caching for suggest/trending is folded in here
 * rather than behind a new port: there's no swappable-backend reason to abstract it, same
 * treatment as the *Properties config beans.
 */
@Service
public class SearchServiceImpl {

    private static final int SUGGEST_LIMIT = 10;
    private static final int TRENDING_LIMIT = 10;
    private static final int HISTORY_LIMIT = 20;
    private static final Duration SUGGEST_CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration TRENDING_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration TRENDING_WINDOW = Duration.ofHours(24);
    private static final String TRENDING_CACHE_KEY = "search:trending";

    private final SearchQueryGateway searchQueryGateway;
    private final SearchQueryLogRepository searchQueryLogRepository;
    private final ImageSearchProvider imageSearchProvider;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SearchServiceImpl(SearchQueryGateway searchQueryGateway, SearchQueryLogRepository searchQueryLogRepository,
                              ImageSearchProvider imageSearchProvider, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.searchQueryGateway = searchQueryGateway;
        this.searchQueryLogRepository = searchQueryLogRepository;
        this.imageSearchProvider = imageSearchProvider;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<ProductSearchHit> search(String query, String lang, String category, int limit, UUID userId) {
        logQuery(query, lang, userId);
        return searchQueryGateway.search(query, lang, category, limit);
    }

    public List<String> suggest(String prefix, String lang) {
        String cacheKey = "search:suggest:" + lang + ":" + prefix;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return readStringList(cached);
        }
        List<String> suggestions = searchQueryGateway.suggest(prefix, lang, SUGGEST_LIMIT);
        redis.opsForValue().set(cacheKey, writeStringList(suggestions), SUGGEST_CACHE_TTL);
        return suggestions;
    }

    public List<String> trending() {
        String cached = redis.opsForValue().get(TRENDING_CACHE_KEY);
        if (cached != null) {
            return readStringList(cached);
        }
        List<String> topQueries = searchQueryLogRepository.findTrending(Instant.now().minus(TRENDING_WINDOW), TRENDING_LIMIT)
                .stream()
                .map(TrendingQueryCount::queryText)
                .toList();
        redis.opsForValue().set(TRENDING_CACHE_KEY, writeStringList(topQueries), TRENDING_CACHE_TTL);
        return topQueries;
    }

    public List<UUID> searchByImage(byte[] image) {
        return imageSearchProvider.matchByImage(image);
    }

    public List<SearchQueryLogEntry> getHistory(UUID userId) {
        return searchQueryLogRepository.findByUserId(userId, HISTORY_LIMIT);
    }

    @Transactional
    public void clearHistory(UUID userId) {
        searchQueryLogRepository.deleteByUserId(userId);
    }

    private void logQuery(String query, String lang, UUID userId) {
        if (query == null || query.isBlank()) {
            return;
        }
        SearchQueryLogEntry entry = new SearchQueryLogEntry();
        entry.setUserId(userId);
        entry.setQueryText(query);
        entry.setLang(lang);
        searchQueryLogRepository.save(entry);
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached search list", e);
        }
    }

    private String writeStringList(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize search list for caching", e);
        }
    }
}
