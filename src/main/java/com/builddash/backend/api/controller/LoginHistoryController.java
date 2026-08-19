package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.response.LoginEventResponse;
import com.builddash.backend.api.mapper.LoginEventMapper;
import com.builddash.backend.application.service.LoginHistoryReader;
import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users/me/login-history")
@Tag(name = "Login History", description = "Audit log of account access")
@SecurityRequirement(name = "bearerAuth")
public class LoginHistoryController {

    private final LoginHistoryReader loginHistoryReader;
    private final LoginEventMapper loginEventMapper;

    public LoginHistoryController(LoginHistoryReader loginHistoryReader, LoginEventMapper loginEventMapper) {
        this.loginHistoryReader = loginHistoryReader;
        this.loginEventMapper = loginEventMapper;
    }

    @GetMapping
    @Operation(summary = "Get my login history", description = "Timestamped log of login events (OTP/Google) with IP + device fingerprint, newest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login history returned"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<LoginEventResponse> getLoginHistory(@AuthenticationPrincipal AuthenticatedUser principal) {
        return loginEventMapper.toResponseList(loginHistoryReader.list(principal.userId()));
    }
}
