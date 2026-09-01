package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.response.StatementResponse;
import com.builddash.backend.application.service.StatementQueryService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Company Statements", description = "B2B monthly statements (9-E, STATEMENT_VIEW)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class StatementController {

    private final StatementQueryService statementQueryService;

    @GetMapping("/companies/{companyId}/statements")
    @Operation(summary = "List statements (latest version per period, newest first)")
    public List<StatementResponse> list(@PathVariable("companyId") UUID companyId,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return statementQueryService.list(user.userId(), companyId).stream()
                .map(StatementResponse::from)
                .toList();
    }

    @GetMapping("/statements/{id}")
    @Operation(summary = "Statement detail by id — historical READY versions stay accessible")
    public StatementResponse get(@PathVariable("id") UUID statementId,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return StatementResponse.from(statementQueryService.get(user.userId(), statementId));
    }
}
