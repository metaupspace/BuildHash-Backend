package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.CreateSupportTicketRequest;
import com.builddash.backend.api.dto.request.SupportTicketMessageRequest;
import com.builddash.backend.api.dto.response.SupportTicketMessageResponse;
import com.builddash.backend.api.dto.response.SupportTicketResponse;
import com.builddash.backend.api.mapper.SupportDtoMapper;
import com.builddash.backend.application.service.SupportTicketService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService supportTicketService;
    private final SupportDtoMapper supportDtoMapper;

    @PostMapping("/support/tickets")
    @Operation(summary = "Create a support ticket; ticket and first message are written in one transaction")
    public ResponseEntity<SupportTicketResponse> createTicket(
            @Valid @RequestBody CreateSupportTicketRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        SupportTicketResponse response = supportDtoMapper.toResponse(supportTicketService.createTicket(
                user.userId(), request.category(), request.subject(), request.message()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/support/tickets")
    @Operation(summary = "List the caller's own support tickets")
    public List<SupportTicketResponse> listOwnTickets(
            @AuthenticationPrincipal AuthenticatedUser user,
            @org.springframework.web.bind.annotation.RequestParam(name = "page", defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(name = "size", defaultValue = "20") int size) {
        return supportTicketService.listOwnTickets(user.userId(), page, size).stream()
                .map(supportDtoMapper::toResponse)
                .toList();
    }

    @GetMapping("/support/tickets/{id}")
    @Operation(summary = "Get one ticket — own only for customers, any for VENDOR/ADMIN; non-owner gets 404")
    public SupportTicketResponse getTicket(
            @PathVariable("id") UUID ticketId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        return supportDtoMapper.toResponse(supportTicketService.getTicket(user.userId(), user.roles(), ticketId));
    }

    @PostMapping("/support/tickets/{id}/messages")
    @Operation(summary = "Append a message — customers on own tickets, agents on any; senderRole derived from role claim")
    public ResponseEntity<SupportTicketMessageResponse> appendMessage(
            @PathVariable("id") UUID ticketId,
            @Valid @RequestBody SupportTicketMessageRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        SupportTicketMessageResponse response = supportDtoMapper.toResponse(
                supportTicketService.appendMessage(user.userId(), user.roles(), ticketId, request.message()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/support/tickets/{id}/escalate")
    @Operation(summary = "Escalate a ticket — VENDOR or ADMIN only (real Spring Security check)")
    public SupportTicketResponse escalate(
            @PathVariable("id") UUID ticketId,
            @AuthenticationPrincipal AuthenticatedUser user) {

        return supportDtoMapper.toResponse(supportTicketService.escalate(ticketId));
    }
}
