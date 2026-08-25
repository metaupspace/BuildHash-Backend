package com.builddash.backend.infra.gateway;

import com.builddash.backend.domain.port.CallProxyGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("!prod")
@Slf4j
public class DummyCallProxyGatewayAdapter implements CallProxyGateway {

    @Override
    public void initiateCall(UUID orderId, UUID customerId, String driverPhone) {
        log.info("Initiating dummy masked proxy call: orderId={}, customerId={}, driverPhone={}",
                orderId, customerId, driverPhone);
    }
}
