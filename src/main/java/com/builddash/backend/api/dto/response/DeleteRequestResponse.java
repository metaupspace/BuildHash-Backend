package com.builddash.backend.api.dto.response;

import java.time.Instant;

/** 202 body for POST /users/me/delete-request (PLAN_PHASE8 §8 skeleton). */
public record DeleteRequestResponse(Instant deletionScheduledAt) {
}
