package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.response.DeliverySlotOptionResponse;
import com.builddash.backend.api.mapper.DeliverySlotDtoMapper;
import com.builddash.backend.application.service.DeliverySlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/delivery-slots")
@Tag(name = "Delivery Slots", description = "Query available warehouse delivery windows")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class DeliverySlotController {

    private final DeliverySlotService deliverySlotService;
    private final DeliverySlotDtoMapper deliverySlotDtoMapper;

    @GetMapping
    @Operation(summary = "Get available delivery slots for a given date")
    public List<DeliverySlotOptionResponse> getDeliverySlots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        return deliverySlotDtoMapper.toResponseList(deliverySlotService.getAvailableSlots(targetDate));
    }
}
