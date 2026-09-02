package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.LoginEvent;

import java.util.List;
import java.util.UUID;

public interface LoginEventRepository {

    LoginEvent save(LoginEvent event);

    List<LoginEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<LoginEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, int page, int size);

    /** DPDP hard-delete (PLAN_PHASE8 5(d)). */
    void deleteByUserId(UUID userId);
}
