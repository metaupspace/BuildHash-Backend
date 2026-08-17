package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.LoginEvent;

import java.util.List;
import java.util.UUID;

public interface LoginEventRepository {

    LoginEvent save(LoginEvent event);

    List<LoginEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
