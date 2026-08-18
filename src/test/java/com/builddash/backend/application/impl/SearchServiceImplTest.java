package com.builddash.backend.application.impl;

import com.builddash.backend.domain.model.TrendingQueryCount;
import com.builddash.backend.domain.port.ImageSearchProvider;
import com.builddash.backend.domain.port.SearchQueryGateway;
import com.builddash.backend.domain.port.SearchQueryLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class SearchServiceImplTest {

    private final SearchQueryGateway gateway = mock(SearchQueryGateway.class);
    private final SearchQueryLogRepository logRepository = mock(SearchQueryLogRepository.class);
    private final ImageSearchProvider imageSearchProvider = mock(ImageSearchProvider.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SearchServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new SearchServiceImpl(gateway, logRepository, imageSearchProvider, redis, objectMapper);
    }

    @Test
    void search_logsQueryThenDelegatesToGateway() {
        UUID userId = UUID.randomUUID();
        when(gateway.search("cement", "en", "Cement", 20)).thenReturn(List.of());

        service.search("cement", "en", "Cement", 20, userId);

        verify(logRepository).save(argThat(entry ->
                userId.equals(entry.getUserId()) && "cement".equals(entry.getQueryText())));
        verify(gateway).search("cement", "en", "Cement", 20);
    }

    @Test
    void search_blankQuery_doesNotLog() {
        service.search("", "en", null, 20, null);

        verify(logRepository, never()).save(any());
    }

    @Test
    void suggest_cacheHit_neverCallsGateway() throws Exception {
        when(valueOps.get("search:suggest:en:cem")).thenReturn(objectMapper.writeValueAsString(List.of("cement")));

        List<String> result = service.suggest("cem", "en");

        assertThat(result).containsExactly("cement");
        verify(gateway, never()).suggest(any(), any(), anyInt());
    }

    @Test
    void suggest_cacheMiss_callsGatewayAndCaches() {
        when(valueOps.get(any())).thenReturn(null);
        when(gateway.suggest("cem", "en", 10)).thenReturn(List.of("cement"));

        List<String> result = service.suggest("cem", "en");

        assertThat(result).containsExactly("cement");
        verify(valueOps).set(eq("search:suggest:en:cem"), any(), any(Duration.class));
    }

    @Test
    void trending_cacheMiss_aggregatesFromLogRepository() {
        when(valueOps.get("search:trending")).thenReturn(null);
        when(logRepository.findTrending(any(), eq(10))).thenReturn(List.of(new TrendingQueryCount("cement", 5)));

        List<String> result = service.trending();

        assertThat(result).containsExactly("cement");
    }
}
