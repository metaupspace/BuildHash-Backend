package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.ProductSearchHit;
import com.builddash.backend.domain.model.SearchQueryLogEntry;

import java.util.List;
import java.util.UUID;

public interface SearchService {

    List<ProductSearchHit> search(String query, String lang, String category, int limit, UUID userId);

    List<String> suggest(String prefix, String lang);

    List<String> trending();

    List<UUID> searchByImage(byte[] image);

    List<SearchQueryLogEntry> getHistory(UUID userId);

    void clearHistory(UUID userId);
}
