package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.CompanyMemberRequest;
import com.builddash.backend.api.dto.response.CompanyMemberResponse;
import com.builddash.backend.application.service.CompanyMembershipService;
import com.builddash.backend.common.AuthenticatedUser;
import com.builddash.backend.domain.model.CompanyMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/companies/{companyId}/members")
@Tag(name = "Company Members", description = "B2B company membership and site assignments")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CompanyMemberController {

    private final CompanyMembershipService membershipService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a member with role and site scope (MEMBER_MANAGE)")
    public CompanyMemberResponse add(@PathVariable UUID companyId,
                                     @Valid @RequestBody CompanyMemberRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        CompanyMember member = membershipService.addMember(companyId, user.userId(),
                request.memberUserId(), request.role(), request.siteIds());
        return CompanyMemberResponse.from(member, membershipService.siteIdsFor(member.id()));
    }

    @GetMapping
    @Operation(summary = "List members (MEMBER_VIEW)")
    public List<CompanyMemberResponse> list(@PathVariable UUID companyId,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        return membershipService.listMembers(companyId, user.userId()).stream()
                .map(m -> CompanyMemberResponse.from(m, membershipService.siteIdsFor(m.id())))
                .toList();
    }

    @PatchMapping("/{memberId}")
    @Operation(summary = "Change a member's role and/or site scope (MEMBER_MANAGE)")
    public CompanyMemberResponse update(@PathVariable UUID companyId,
                                        @PathVariable UUID memberId,
                                        @Valid @RequestBody CompanyMemberRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        CompanyMember member = membershipService.updateMember(companyId, user.userId(),
                memberId, request.role(), request.siteIds());
        return CompanyMemberResponse.from(member, membershipService.siteIdsFor(member.id()));
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member (MEMBER_MANAGE, last-owner protected)")
    public void remove(@PathVariable UUID companyId,
                       @PathVariable UUID memberId,
                       @AuthenticationPrincipal AuthenticatedUser user) {
        membershipService.removeMember(companyId, user.userId(), memberId);
    }

    @PostMapping("/transfer-ownership")
    @Operation(summary = "Transfer ownership to another member (OWNER only; old OWNER becomes PROCUREMENT_MANAGER)")
    public void transferOwnership(@PathVariable UUID companyId,
                                  @RequestParam UUID targetMemberId,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        membershipService.transferOwnership(companyId, user.userId(), targetMemberId);
    }
}
