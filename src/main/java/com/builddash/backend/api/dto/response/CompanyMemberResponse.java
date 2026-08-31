package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.CompanyMember;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CompanyMemberResponse(
        UUID id,
        UUID userId,
        String role,
        List<UUID> siteIds,
        Instant createdAt
) {

    public static CompanyMemberResponse from(CompanyMember member, List<UUID> siteIds) {
        return new CompanyMemberResponse(member.id(), member.userId(), member.role().name(),
                siteIds, member.createdAt());
    }
}
