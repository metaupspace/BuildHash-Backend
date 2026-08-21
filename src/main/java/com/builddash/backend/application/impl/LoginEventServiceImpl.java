package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.LoginEventRecorder;
import com.builddash.backend.application.service.LoginHistoryReader;
import com.builddash.backend.domain.enums.LoginEventType;
import com.builddash.backend.domain.model.LoginEvent;
import com.builddash.backend.domain.port.LoginEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class LoginEventServiceImpl implements LoginEventRecorder, LoginHistoryReader {

    private final LoginEventRepository loginEventRepository;


    @Override
    @Transactional
    public void record(UUID userId, LoginEventType type, String ipAddress, String deviceFingerprint) {
        LoginEvent event = new LoginEvent();
        event.setUserId(userId);
        event.setEventType(type);
        event.setIpAddress(ipAddress);
        event.setDeviceFingerprint(deviceFingerprint);
        loginEventRepository.save(event);
    }

    @Override
    public List<LoginEvent> list(UUID userId) {
        return loginEventRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
