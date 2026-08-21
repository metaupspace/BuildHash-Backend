package com.builddash.backend.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Adapter (Open Question #2, resolved: third-party vision/matching API, not a self-hosted
 * embedding model) — isolates the vendor choice so it's swappable without touching the
 * controller or contract.
 */
public interface ImageSearchProvider {

    List<UUID> matchByImage(byte[] image);
}
