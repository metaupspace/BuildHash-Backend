package com.builddash.backend.domain.port;

import java.util.UUID;

public interface CallProxyGateway {
    void initiateCall(UUID orderId, UUID customerId, String driverPhone);
}
