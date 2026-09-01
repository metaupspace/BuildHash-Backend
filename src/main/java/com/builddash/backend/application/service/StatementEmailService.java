package com.builddash.backend.application.service;

/** Statement email delivery (9-E) — separate from generation; READY never depends on it. */
public interface StatementEmailService {

    /** Delivers READY statements with outstanding email, bounded per pass. */
    int sweep();

    /** One delivery: size-gate before loading bytes, send outside tx, mark in short tx. */
    void deliver(java.util.UUID statementId);
}
