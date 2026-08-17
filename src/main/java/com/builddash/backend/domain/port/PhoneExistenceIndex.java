package com.builddash.backend.domain.port;

/**
 * SRP: a cheap, probabilistic "have we seen this phone before" pre-check — nothing about how
 * that fact is stored (Bloom filter today) or where the ground truth (Postgres) lives.
 * mightExist() can have false positives (never false negatives once marked) — callers must
 * treat a "true" result as advisory only, never as a substitute for an authoritative check.
 */
public interface PhoneExistenceIndex {

    boolean mightExist(String phone);

    void markExists(String phone);
}
