package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.DelegateApprovalRequest;
import com.builddash.backend.api.dto.response.ApprovalResponse;
import com.builddash.backend.application.service.ApprovalService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approvals")
@Tag(name = "Approvals", description = "B2B order approval gate (9-D)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping
    @Operation(summary = "List the caller's company approval requests (APPROVAL_VIEW, site-scoped)")
    public List<ApprovalResponse> list(
            @RequestParam("companyId") UUID companyId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return approvalService.list(user.userId(), companyId, page, size).stream()
                .map(ApprovalResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Approval request detail with action history")
    public ApprovalResponse get(@PathVariable("id") UUID approvalId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return ApprovalResponse.from(approvalService.get(user.userId(), approvalId));
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Approve and resume payment (APPROVAL_ACT; self-approval prohibited)")
    public ApprovalResponse approve(@PathVariable("id") UUID approvalId,
                                    @AuthenticationPrincipal AuthenticatedUser user) {
        return ApprovalResponse.from(approvalService.approve(user.userId(), approvalId));
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Reject and cancel the order (APPROVAL_ACT)")
    public ApprovalResponse reject(@PathVariable("id") UUID approvalId,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return ApprovalResponse.from(approvalService.reject(user.userId(), approvalId));
    }

    @PostMapping("/{id}/delegate")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Delegate this pending approval once (APPROVAL_DELEGATE)")
    public ApprovalResponse delegate(@PathVariable("id") UUID approvalId,
                                     @Valid @RequestBody DelegateApprovalRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        return ApprovalResponse.from(approvalService.delegate(user.userId(), approvalId,
                request.delegateMemberId()));
    }
}
