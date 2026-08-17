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
}
