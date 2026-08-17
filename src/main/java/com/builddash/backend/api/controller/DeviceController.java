package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.response.AuthTokensResponse;
import com.builddash.backend.api.dto.response.DeviceResponse;
import com.builddash.backend.api.mapper.AuthMapper;
import com.builddash.backend.api.mapper.DeviceMapper;
import com.builddash.backend.application.service.AuthenticationFacade;
import com.builddash.backend.application.service.DeviceRegistry;
import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/me")
@Tag(name = "Device & Session Registry", description = "List, revoke, and bulk-invalidate logged-in devices")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {

    private final DeviceRegistry deviceRegistry;
    private final AuthenticationFacade authenticationFacade;
    private final DeviceMapper deviceMapper;
    private final AuthMapper authMapper;

    public DeviceController(DeviceRegistry deviceRegistry, AuthenticationFacade authenticationFacade,
                             DeviceMapper deviceMapper, AuthMapper authMapper) {
        this.deviceRegistry = deviceRegistry;
        this.authenticationFacade = authenticationFacade;
        this.deviceMapper = deviceMapper;
        this.authMapper = authMapper;
    }

    @GetMapping("/devices")
    @Operation(summary = "List my active devices", description = "Every device currently logged into this account, for security awareness.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active devices returned"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public List<DeviceResponse> listDevices(@AuthenticationPrincipal AuthenticatedUser principal) {
        return deviceMapper.toResponseList(deviceRegistry.listActive(principal.userId()));
    }

    @DeleteMapping("/devices/{deviceId}")
    @Operation(summary = "Revoke one device", description = "Invalidates that device's refresh token so it can no longer mint new access tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Device revoked"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Device does not exist or does not belong to you",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<Void> revokeDevice(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable UUID deviceId) {
        deviceRegistry.revoke(principal.userId(), deviceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all-devices")
    @Operation(summary = "Logout from all devices",
            description = "Emergency 'kick everyone out' action for a compromised account — invalidates every "
                    + "active session at once, then immediately issues a fresh session for the device making this "
                    + "request so you aren't locked out of the action you just took.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All devices logged out, fresh tokens issued for this device"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AuthTokensResponse logoutAllDevices(@AuthenticationPrincipal AuthenticatedUser principal) {
        return authMapper.toResponse(authenticationFacade.logoutAllDevicesAndReissue(principal.userId(), principal.deviceId()));
    }
}
