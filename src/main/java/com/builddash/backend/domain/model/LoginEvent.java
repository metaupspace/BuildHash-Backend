package com.builddash.backend.domain.model;

import com.builddash.backend.domain.enums.LoginEventType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class LoginEvent {

    private UUID id;
    private UUID userId;
    private LoginEventType eventType;
    private String ipAddress;
    private String deviceFingerprint;
    private Instant createdAt;
}
