package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.LoginEventResponse;
import com.builddash.backend.domain.model.LoginEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoginEventMapper {

    public LoginEventResponse toResponse(LoginEvent event) {
        return new LoginEventResponse(event.getId(), event.getEventType(), event.getIpAddress(),
                event.getDeviceFingerprint(), event.getCreatedAt());
    }

    public List<LoginEventResponse> toResponseList(List<LoginEvent> events) {
        return events.stream().map(this::toResponse).toList();
    }
}
