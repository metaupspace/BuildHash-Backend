package com.builddash.backend.domain.port;

public interface ServiceabilityGateway {
    /**
     * Validates if a set of coordinates falls within an active service radius.
     */
    boolean isServiceable(double lat, double lng);
}
