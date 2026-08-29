package com.builddash.backend.infra.crypto;

/**
 * Master-key custody seam (PLAN_PHASE8 decision 2). The config-backed implementation ships
 * this phase; prod swaps in KMS (cloud topology) or OpenBao (self-hosted) behind this
 * interface without touching any converter.
 */
public interface PiiKeyProvider {

    /**
     * Derives a purpose-bound subkey from the master key. Single-block HKDF-Expand
     * equivalent: HMAC-SHA256(master, label) — every label yields an independent 32-byte
     * key; field FQNs + purpose ("aes"/"hmac") are the labels.
     */
    byte[] derivedKey(String label);
}
