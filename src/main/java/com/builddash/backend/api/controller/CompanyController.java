package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.CompanyCreateRequest;
import com.builddash.backend.api.dto.response.CompanyResponse;
import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.model.Company;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/companies")
@Tag(name = "Companies", description = "B2B company accounts")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a company (creator becomes OWNER)")
    public CompanyResponse create(@Valid @RequestBody CompanyCreateRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        Company company = companyService.create(user.userId(), request.name(), request.gstNumber(),
                request.statementEmail(), request.businessTimezone());
        return CompanyResponse.from(company);
    }

    @GetMapping("/{companyId}")
    @Operation(summary = "Get a company (COMPANY_VIEW)")
    public CompanyResponse get(@PathVariable UUID companyId,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        return CompanyResponse.from(companyService.get(companyId, user.userId()));
    }

    @PatchMapping("/{companyId}")
    @Operation(summary = "Update company profile (COMPANY_UPDATE)")
    public CompanyResponse update(@PathVariable UUID companyId,
                                  @Valid @RequestBody CompanyCreateRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return CompanyResponse.from(companyService.update(companyId, user.userId(), request.name(),
                request.gstNumber(), request.statementEmail(), request.businessTimezone()));
    }

    @PatchMapping("/{companyId}/status")
    @Operation(summary = "Suspend or activate a company (COMPANY_UPDATE)")
    public CompanyResponse updateStatus(@PathVariable UUID companyId,
                                        @RequestParam CompanyStatus status,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return CompanyResponse.from(companyService.updateStatus(companyId, user.userId(), status));
    }
}
