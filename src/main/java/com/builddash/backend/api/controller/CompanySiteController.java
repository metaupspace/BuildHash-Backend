package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.CompanySiteRequest;
import com.builddash.backend.api.dto.response.CompanySiteResponse;
import com.builddash.backend.application.service.CompanySiteService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/companies/{companyId}/sites")
@Tag(name = "Company Sites", description = "B2B company operating sites")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CompanySiteController {

    private final CompanySiteService siteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a site (SITE_MANAGE)")
    public CompanySiteResponse create(@PathVariable UUID companyId,
                                      @Valid @RequestBody CompanySiteRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return CompanySiteResponse.from(siteService.create(companyId, user.userId(),
                request.name(), request.addressId()));
    }

    @GetMapping
    @Operation(summary = "List sites (SITE_VIEW)")
    public List<CompanySiteResponse> list(@PathVariable UUID companyId,
                                          @AuthenticationPrincipal AuthenticatedUser user) {
        return siteService.listSites(companyId, user.userId()).stream()
                .map(CompanySiteResponse::from)
                .toList();
    }

    @PatchMapping("/{siteId}")
    @Operation(summary = "Update a site; deactivation blocked while active orders reference it (SITE_MANAGE)")
    public CompanySiteResponse update(@PathVariable UUID companyId,
                                      @PathVariable UUID siteId,
                                      @Valid @RequestBody CompanySiteRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return CompanySiteResponse.from(siteService.update(companyId, siteId, user.userId(),
                request.name(), request.addressId(), request.active()));
    }
}
