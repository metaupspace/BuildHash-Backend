package com.builddash.backend.application.service;

import com.builddash.backend.domain.enums.LoginEventType;

import java.util.UUID;

/**
 * ISP: AuthService only ever writes login events, never reads them back.
 */
public interface LoginEventRecorder {

    void record(UUID userId, LoginEventType type, String ipAddress, String deviceFingerprint);
}
