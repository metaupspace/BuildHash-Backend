package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.ChatRequest;
import com.builddash.backend.api.dto.response.ChatResponse;
import com.builddash.backend.api.mapper.SupportDtoMapper;
import com.builddash.backend.application.service.SupportChatService;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SupportChatController {

    private final SupportChatService supportChatService;
    private final SupportDtoMapper supportDtoMapper;

    @PostMapping("/support/chat")
    @Operation(summary = "Classify a chat message; Phase 7's stub always escalates to a human ticket with the message as context")
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        return supportDtoMapper.toResponse(supportChatService.chat(user.userId(), request.message()));
    }
}
