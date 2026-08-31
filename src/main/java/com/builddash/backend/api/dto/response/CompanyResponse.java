package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.Company;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String gstNumber,
        String statementEmail,
        String businessTimezone,
        String status,
        Instant createdAt
) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(company.id(), company.name(), company.gstNumber(),
                company.statementEmail(), company.businessTimezone(), company.status().name(),
                company.createdAt());
    }
}
