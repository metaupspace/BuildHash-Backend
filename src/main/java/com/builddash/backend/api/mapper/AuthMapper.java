package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.AuthTokensResponse;
import com.builddash.backend.api.dto.response.OtpSendResponse;
import com.builddash.backend.domain.model.AuthSession;
import com.builddash.backend.domain.model.OtpSendResult;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public AuthTokensResponse toResponse(AuthSession session) {
        return new AuthTokensResponse(session.accessToken(), session.refreshToken(),
                session.tokenType(), session.expiresInSeconds());
    }

    public OtpSendResponse toResponse(OtpSendResult result) {
        return new OtpSendResponse("OTP sent", result.expiresInSeconds(), result.existingUser());
    }
}
