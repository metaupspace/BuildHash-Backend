package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.DeleteRequest;

import java.util.UUID;

public interface DeleteRequestService {

    /**
     * Creates the pending deletion request. Throws DeleteRequestPendingException (409) when
     * one is already pending — from the fast-path check OR from the DB partial unique index
     * under a race.
     */
    DeleteRequest requestDeletion(UUID userId);
}
