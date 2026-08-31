package com.builddash.backend.domain.port;

import java.time.Duration;

public interface ObjectStorage {
    String store(String key, byte[] bytes, String contentType);
    String signedUrl(String key, Duration ttl);

    /** Best-effort object removal (DPDP: S3 return photos). Absent key is not an error. */
    void delete(String key);
}
