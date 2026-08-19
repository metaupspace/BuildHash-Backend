package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.request.GoogleSignInRequest;
import com.builddash.backend.api.dto.request.OtpSendRequest;
import com.builddash.backend.api.dto.request.OtpVerifyRequest;
import com.builddash.backend.api.dto.request.RefreshRequest;
import com.builddash.backend.api.dto.response.AuthTokensResponse;
import com.builddash.backend.api.dto.response.OtpSendResponse;
import com.builddash.backend.api.mapper.AuthMapper;
import com.builddash.backend.application.service.AuthenticationFacade;
import com.builddash.backend.api.dto.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "OTP login, Google sign-in, guest sessions, and token refresh")
public class AuthController {

    private final AuthenticationFacade authenticationFacade;
    private final AuthMapper authMapper;

    public AuthController(AuthenticationFacade authenticationFacade, AuthMapper authMapper) {
        this.authenticationFacade = authenticationFacade;
        this.authMapper = authMapper;
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP", description = "Generates a 6-digit OTP and dispatches it via SMS. "
            + "Rate-limited to 5 sends/hour per phone number, with a short cooldown between consecutive sends.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP sent"),
            @ApiResponse(responseCode = "400", description = "Invalid phone number format",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Resend cooldown active, or the hourly send limit was exceeded",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = "{\"status\":429,\"code\":\"OTP_RATE_LIMIT_EXCEEDED\","
                                    + "\"message\":\"Too many OTP requests for this phone number, try again later\"}")))
    })
    public OtpSendResponse sendOtp(@Valid @RequestBody OtpSendRequest request) {
        return authMapper.toResponse(authenticationFacade.sendOtp(request.phone()));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP", description = "Confirms the OTP, creates the user account on first login, "
            + "and issues an access + refresh token pair for a new device session. Wrong OTP is rejected with 401; "
            + "after 3 wrong attempts the phone number is locked (423) until a new OTP is requested.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP verified, tokens issued"),
            @ApiResponse(responseCode = "400", description = "OTP expired or was never requested",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = "{\"status\":400,\"code\":\"OTP_EXPIRED\","
                                    + "\"message\":\"OTP has expired or was not requested\"}"))),
            @ApiResponse(responseCode = "401", description = "Incorrect OTP",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = "{\"status\":401,\"code\":\"OTP_INCORRECT\","
                                    + "\"message\":\"Incorrect OTP\"}"))),
            @ApiResponse(responseCode = "423", description = "Locked after 3 consecutive wrong attempts",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = "{\"status\":423,\"code\":\"OTP_LOCKED\","
                                    + "\"message\":\"Too many incorrect attempts, request a new OTP\"}")))
    })
    public AuthTokensResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request, HttpServletRequest httpRequest) {
        return authMapper.toResponse(authenticationFacade.verifyOtp(
                request.phone(), request.otp(), request.deviceFingerprint(), clientIp(httpRequest)));
    }

    @PostMapping("/google")
    @Operation(summary = "Sign in with Google", description = "Verifies the ID token server-side against Google's "
            + "public keys, then matches or creates the account by Google subject/email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token verified, tokens issued"),
            @ApiResponse(responseCode = "401", description = "Google ID token is invalid, expired, or fails audience verification",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AuthTokensResponse googleSignIn(@Valid @RequestBody GoogleSignInRequest request, HttpServletRequest httpRequest) {
        return authMapper.toResponse(authenticationFacade.googleSignIn(
                request.idToken(), request.deviceFingerprint(), clientIp(httpRequest)));
    }

    @PostMapping("/guest")
    @Operation(summary = "Start a guest session", description = "Issues a scoped, non-refreshable token for "
            + "browsing/cart use without creating an account. Rejected by any endpoint requiring ROLE_USER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Guest token issued")
    })
    public AuthTokensResponse guestSession() {
        return authMapper.toResponse(authenticationFacade.guestSession());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh tokens", description = "Rotates the refresh token: validates the presented "
            + "refresh token against the device record, then issues a brand-new access + refresh pair. "
            + "Reusing an already-rotated (stale) refresh token is treated as compromise and revokes the device.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "401", description = "Refresh token is invalid, expired, revoked, or was already rotated (reuse)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiError.class),
                            examples = @ExampleObject(value = "{\"status\":401,\"code\":\"INVALID_REFRESH_TOKEN\","
                                    + "\"message\":\"Refresh token is invalid or has been revoked\"}")))
    })
    public AuthTokensResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authMapper.toResponse(authenticationFacade.refresh(request.refreshToken()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
