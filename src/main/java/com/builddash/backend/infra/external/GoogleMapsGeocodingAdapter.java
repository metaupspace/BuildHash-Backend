package com.builddash.backend.infra.external;

import com.builddash.backend.domain.port.GeocodingGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stubbed adapter for Google Maps geocoding.
 * In a real implementation, this would call the Google Maps Geocoding API.
 */
@Component
@Profile("!prod")
@Slf4j
public class GoogleMapsGeocodingAdapter implements GeocodingGateway {

    @Override
    public Optional<Coordinates> geocode(String line1, String line2, String city, String state, String zipCode) {
        // Stub: always return a dummy coordinate for now, to allow serviceability tests to pass.
        // E.g., somewhere in central India
        log.info("Stubbing geocode for {} {} {}", line1, city, zipCode);
        return Optional.of(new Coordinates(21.1458, 79.0882));
    }
}
