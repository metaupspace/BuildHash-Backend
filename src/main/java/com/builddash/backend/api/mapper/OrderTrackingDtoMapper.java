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
                ? new DriverDto("Delivery Driver", maskPhone(tracking.driverPhone()), tracking.driverPhone() != null && !tracking.driverPhone().isBlank())
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

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String clean = phone.trim();
        if (clean.length() <= 4) return "****";
        String last4 = clean.substring(clean.length() - 4);
        if (clean.startsWith("+91") && clean.length() > 6) {
            return "+91 ******" + last4;
        }
        return "******" + last4;
    }
}
