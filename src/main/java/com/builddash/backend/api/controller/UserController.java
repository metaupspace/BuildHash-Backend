package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.UpdateProfileRequest;
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

@RestController
@RequestMapping("/users")
@Tag(name = "User Profile", description = "Authenticated user's own profile")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserProfileReader userProfileReader;
    private final UserProfileWriter userProfileWriter;
    private final UserMapper userMapper;

    public UserController(UserProfileReader userProfileReader, UserProfileWriter userProfileWriter, UserMapper userMapper) {
        this.userProfileReader = userProfileReader;
        this.userProfileWriter = userProfileWriter;
        this.userMapper = userMapper;
    }

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
}
