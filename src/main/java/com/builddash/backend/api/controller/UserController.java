package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.UpdateProfileRequest;
import com.builddash.backend.api.dto.response.DeleteRequestResponse;
import com.builddash.backend.api.dto.response.UserProfileResponse;
import com.builddash.backend.api.mapper.UserMapper;
import com.builddash.backend.application.service.UserProfileReader;
import com.builddash.backend.application.service.UserProfileWriter;
import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.common.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@Tag(name = "User Profile", description = "Authenticated user's own profile")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileReader userProfileReader;
    private final UserProfileWriter userProfileWriter;
    private final UserMapper userMapper;
    private final com.builddash.backend.domain.port.UserDataExporter userDataExporter;
    private final com.builddash.backend.application.service.DeleteRequestService deleteRequestService;


    @GetMapping("/me")
    @Operation(summary = "Get my profile", description = "Returns the authenticated user's profile.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserProfileResponse getMe(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userMapper.toProfileResponse(userProfileReader.getProfile(principal.userId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update my profile",
            description = "Updates name, business name, and/or GST number. Fields left null are left unchanged. "
                    + "GST number is format-validated only in Phase 0 — setting it marks gstinStatus as PENDING "
                    + "(no external GST-portal verification call is made yet).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Invalid GST number format",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = "{\"status\":400,\"code\":\"VALIDATION_FAILED\","
                                    + "\"message\":\"gstNumber: GST number format is invalid\"}"))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserProfileResponse updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                         @Valid @RequestBody UpdateProfileRequest request) {
        return userMapper.toProfileResponse(userProfileWriter.updateProfile(
                principal.userId(), request.name(), request.businessName(), request.gstNumber()));
    }

    @GetMapping("/me/export")
    @Operation(summary = "Export my data (DPDP)", description = "Returns the caller's full data footprint as one "
            + "synchronous JSON document — one section per table of the user-data inventory (profile, addresses, "
            + "orders, returns, notifications, support tickets, ...). Sparse sections are empty arrays, never missing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Export document returned"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public com.builddash.backend.domain.model.UserDataExport exportMe(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return userDataExporter.export(principal.userId());
    }

    @org.springframework.web.bind.annotation.PostMapping("/me/delete-request")
    @Operation(summary = "Request account deletion (DPDP)", description = "Schedules the account for deletion after "
            + "the configured grace period (default 30 days). Financial records are retained for tax compliance; "
            + "personal data is hard-deleted; the profile is anonymized to a tombstone.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Deletion scheduled",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = DeleteRequestResponse.class),
                            examples = @ExampleObject(value = "{\"deletionScheduledAt\":\"2026-09-28T15:00:00Z\"}"))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid/expired access token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "A deletion request is already pending",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = "{\"status\":409,\"code\":\"DELETE_REQUEST_PENDING\","
                                    + "\"message\":\"A deletion request is already pending for this account\"}")))
    })
    org.springframework.http.ResponseEntity<DeleteRequestResponse> requestDeletion(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        var request = deleteRequestService.requestDeletion(principal.userId());
        return org.springframework.http.ResponseEntity.accepted()
                .body(new DeleteRequestResponse(request.deletionScheduledAt()));
    }
}
