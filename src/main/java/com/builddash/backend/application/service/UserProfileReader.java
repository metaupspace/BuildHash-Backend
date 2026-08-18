package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.User;

import java.util.UUID;

/**
 * ISP: GET /users/me only needs read access — kept separate from UserProfileWriter so a
 * read-only caller can never accidentally depend on write capability. Returns the domain
 * model — api/mapper builds the response DTO in the controller.
 */
public interface UserProfileReader {

    User getProfile(UUID userId);
}
