package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.response.SearchHistoryEntryResponse;
import com.builddash.backend.api.mapper.SearchMapper;
import com.builddash.backend.application.impl.SearchServiceImpl;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/me/search-history")
@Tag(name = "Search History", description = "Capped last-N search history")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final SearchServiceImpl searchService;
    private final SearchMapper searchMapper;


    @GetMapping
    @Operation(summary = "Get my search history", description = "Last 20 queries, newest first.")
    public List<SearchHistoryEntryResponse> getHistory(@AuthenticationPrincipal AuthenticatedUser principal) {
        return searchMapper.toHistoryResponseList(searchService.getHistory(principal.userId()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear my search history")
    public void clearHistory(@AuthenticationPrincipal AuthenticatedUser principal) {
        searchService.clearHistory(principal.userId());
    }
}
