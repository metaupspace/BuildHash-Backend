package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.DeviceResponse;
import com.builddash.backend.domain.model.Device;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeviceMapper {

    public DeviceResponse toResponse(Device device) {
        return new DeviceResponse(device.getId(), device.getDeviceFingerprint(), device.getLastSeenAt(), device.getCreatedAt());
    }

    public List<DeviceResponse> toResponseList(List<Device> devices) {
        return devices.stream().map(this::toResponse).toList();
    }
}
