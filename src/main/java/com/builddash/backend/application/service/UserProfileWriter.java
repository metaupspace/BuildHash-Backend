package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.User;

import java.util.UUID;

/**
 * ISP: PUT /users/me needs write access — kept separate from UserProfileReader. Takes the
 * individual editable fields (not the api/dto/request type) so this layer never depends on
 * api/ — the controller unpacks the request DTO before calling in, and api/mapper builds the
 * response DTO from the returned domain model.
 */
public interface UserProfileWriter {

    User updateProfile(UUID userId, String name, String businessName, String gstNumber);
}
