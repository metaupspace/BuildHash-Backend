package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    List<String> findAllPhones();

    User save(User user);

    /**
     * DPDP tombstone (PLAN_PHASE8 decision 9): nulls identity columns (phone, email, name,
     * business_name, gst_number, google_id) AND their blind-index columns in one UPDATE,
     * keeping the row and id for FK integrity. Repository-level by design — the User
     * aggregate never learns about its own destruction.
     */
    void anonymize(UUID userId);
}
