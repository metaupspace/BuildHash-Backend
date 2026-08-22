package com.builddash.backend.domain.port;

import java.util.Optional;

public interface GeocodingGateway {
    
    record Coordinates(double lat, double lng) {}
    
    /**
     * Attempts to geocode a physical address into latitude/longitude coordinates.
     */
    Optional<Coordinates> geocode(String line1, String line2, String city, String state, String zipCode);
}
