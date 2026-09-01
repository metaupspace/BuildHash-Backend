package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.ApprovalPolicyRequest;
import com.builddash.backend.api.dto.response.ApprovalPolicyResponse;
import com.builddash.backend.application.service.ApprovalPolicyService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/companies/{companyId}/approval-policy")
@Tag(name = "Company Approval Policy", description = "B2B approval gate configuration (9-D, OWNER-only management)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ApprovalPolicyController {

    private final ApprovalPolicyService approvalPolicyService;

    @GetMapping
    @Operation(summary = "Read the company approval policy (COMPANY_VIEW; 404 when none configured)")
    public ApprovalPolicyResponse get(@PathVariable("companyId") UUID companyId,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return ApprovalPolicyResponse.from(approvalPolicyService.get(user.userId(), companyId));
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Replace the policy (OWNER-only, version increment)")
    public ApprovalPolicyResponse put(@PathVariable("companyId") UUID companyId,
                                      @Valid @RequestBody ApprovalPolicyRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return ApprovalPolicyResponse.from(approvalPolicyService.put(user.userId(), companyId,
                new ApprovalPolicyService.Command(request.amountThreshold(), request.categoryIds(),
                        request.siteIds(), request.roleStages(), request.escalationHours())));
    }
}
