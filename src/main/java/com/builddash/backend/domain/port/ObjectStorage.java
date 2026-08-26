package com.builddash.backend.domain.port;

import java.time.Duration;

public interface ObjectStorage {
    String store(String key, byte[] bytes, String contentType);
    String signedUrl(String key, Duration ttl);
}
