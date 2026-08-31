package com.builddash.backend.api.dto.request;

import com.builddash.backend.domain.enums.CompanyPermission;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** Full-set replacement for one non-OWNER role; duplicates collapse (set semantics). */
public record ReplaceRolePermissionsRequest(
        @NotNull Set<CompanyPermission> permissions
) {
}
