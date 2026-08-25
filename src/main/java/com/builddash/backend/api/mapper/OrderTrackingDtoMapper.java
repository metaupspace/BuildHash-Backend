package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.DriverDto;
import com.builddash.backend.api.dto.response.LocationDto;
import com.builddash.backend.api.dto.response.OrderTrackingResponse;
import com.builddash.backend.domain.model.OrderTracking;
import org.springframework.stereotype.Component;

@Component
public class OrderTrackingDtoMapper {

    public OrderTrackingResponse toResponse(OrderTracking tracking) {
        if (tracking == null) return null;
        DriverDto driver = (tracking.driverId() != null || tracking.driverPhone() != null)
                ? new DriverDto(tracking.driverId(), tracking.driverPhone())
                : null;
        LocationDto location = (tracking.latitude() != null || tracking.longitude() != null)
                ? new LocationDto(tracking.latitude(), tracking.longitude())
                : null;
        return new OrderTrackingResponse(
                tracking.status(),
                driver,
                location,
                tracking.updatedAt()
        );
    }
}
