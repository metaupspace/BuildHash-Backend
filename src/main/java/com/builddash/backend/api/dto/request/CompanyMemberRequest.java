package com.builddash.backend.api.dto.request;

import com.builddash.backend.domain.enums.CompanyRole;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** siteIds null = leave unchanged on update; empty = unscoped member (all sites). */
public record CompanyMemberRequest(
        @NotNull UUID memberUserId,
        CompanyRole role,
        List<UUID> siteIds
) {
}
