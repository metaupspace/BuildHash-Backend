package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.LoginEvent;

import java.util.List;
import java.util.UUID;

/**
 * ISP: LoginHistoryController only ever reads, never writes. Returns the domain model —
 * api/mapper builds LoginEventResponse in the controller.
 */
public interface LoginHistoryReader {

    List<LoginEvent> list(UUID userId);
}
