package com.builddash.backend.infra.external;

import com.builddash.backend.domain.port.ServiceabilityGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Static stub mirroring the deferral pattern used for Product.stock in Phase 1.
 * For Phase 3, this simply approves all geocoded addresses as serviceable.
 */
@Component
@Profile("!prod")
public class StubServiceabilityGateway implements ServiceabilityGateway {

    @Override
    public boolean isServiceable(double lat, double lng) {
        // In reality, checks if coordinates fall within warehouse polygons
        return true;
    }
}
