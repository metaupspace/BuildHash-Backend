package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.ProductSearchHitResponse;
import com.builddash.backend.api.dto.response.SearchHistoryEntryResponse;
import com.builddash.backend.api.dto.response.SearchResultResponse;
import com.builddash.backend.api.dto.response.SuggestResponse;
import com.builddash.backend.api.dto.response.TrendingResponse;
import com.builddash.backend.domain.model.ProductSearchHit;
import com.builddash.backend.domain.model.SearchQueryLogEntry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchMapper {

    public SearchResultResponse toSearchResultResponse(List<ProductSearchHit> hits) {
        List<ProductSearchHitResponse> items = hits.stream()
                .map(h -> new ProductSearchHitResponse(h.productId(), h.name(), h.category(), h.brand(), h.stockStatus()))
                .toList();
        return new SearchResultResponse(items);
    }

    public SuggestResponse toSuggestResponse(List<String> suggestions) {
        return new SuggestResponse(suggestions);
    }

    public TrendingResponse toTrendingResponse(List<String> queries) {
        return new TrendingResponse(queries);
    }

    public List<SearchHistoryEntryResponse> toHistoryResponseList(List<SearchQueryLogEntry> entries) {
        return entries.stream()
                .map(e -> new SearchHistoryEntryResponse(e.getQueryText(), e.getCreatedAt()))
                .toList();
    }
}
