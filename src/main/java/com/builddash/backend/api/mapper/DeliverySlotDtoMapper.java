package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.DeliverySlotOptionResponse;
import com.builddash.backend.domain.model.DeliverySlotOption;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeliverySlotDtoMapper {

    public DeliverySlotOptionResponse toResponse(DeliverySlotOption option) {
        if (option == null) return null;
        return new DeliverySlotOptionResponse(
                option.slotId(),
                option.startTime(),
                option.endTime(),
                option.date(),
                option.capacity(),
                option.availableCount()
        );
    }

    public List<DeliverySlotOptionResponse> toResponseList(List<DeliverySlotOption> options) {
        if (options == null) return List.of();
        return options.stream().map(this::toResponse).toList();
    }
}
