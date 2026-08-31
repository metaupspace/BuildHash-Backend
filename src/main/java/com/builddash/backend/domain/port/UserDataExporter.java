package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.UserDataExport;

import java.util.UUID;

/** Assembles the DPDP export document (PLAN_PHASE8 decision 8) — port so the assembler is testable against fakes. */
public interface UserDataExporter {

    /**
     * Throws NotFoundException when the user does not exist — an export is only served to
     * the user's own authenticated session, so absence is a data problem, not a 404-probe.
     */
    UserDataExport export(UUID userId);
}
