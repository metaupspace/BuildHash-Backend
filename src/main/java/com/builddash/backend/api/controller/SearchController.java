package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.response.ImageSearchResponse;
import com.builddash.backend.api.dto.response.SearchResultResponse;
import com.builddash.backend.api.dto.response.SuggestResponse;
import com.builddash.backend.api.dto.response.TrendingResponse;
import com.builddash.backend.api.mapper.SearchMapper;
import com.builddash.backend.application.impl.SearchServiceImpl;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;

@RestController
@Tag(name = "Search", description = "Elasticsearch-backed product search (public, no auth required)")
@RequiredArgsConstructor
public class SearchController {

    private final SearchServiceImpl searchService;
    private final SearchMapper searchMapper;


    @GetMapping("/search")
    @Operation(summary = "Search products", description = "Fuzzy match + Hindi/English alias lookup + category filter, all conditional on which params arrived.")
    public SearchResultResponse search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) String lang,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Max(value = 100, message = "limit must be at most 100") @jakarta.validation.constraints.Min(value = 1, message = "limit must be at least 1") int limit,
                                        @AuthenticationPrincipal(errorOnInvalidType = false) AuthenticatedUser principal) {
        var userId = principal == null ? null : principal.userId();
        return searchMapper.toSearchResultResponse(searchService.search(q, lang, category, limit, userId));
    }

    @GetMapping("/search/suggest")
    @Operation(summary = "Autocomplete suggestions", description = "Redis-cached (60s) — ES-backed autocomplete on the name.autocomplete edge-ngram field.")
    public SuggestResponse suggest(@RequestParam String q, @RequestParam(required = false) String lang) {
        return searchMapper.toSuggestResponse(searchService.suggest(q, lang));
    }

    @GetMapping("/search/trending")
    @Operation(summary = "Trending searches", description = "Top queries over the last 24h, Redis-cached (10min, compute-on-miss).")
    public TrendingResponse trending() {
        return searchMapper.toTrendingResponse(searchService.trending());
    }

    @PostMapping(value = "/search/image", consumes = "multipart/form-data")
    @Operation(summary = "Search by image (stub)", description = "Contract only for Phase 1 — ImageSearchProvider is a stub returning zero matches until a vendor is chosen (Open Question #2).")
    public ImageSearchResponse searchByImage(@RequestPart("image") MultipartFile image) {
        try {
            return new ImageSearchResponse(searchService.searchByImage(image.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded image", e);
        }
    }
}
