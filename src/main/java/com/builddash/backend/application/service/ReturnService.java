package com.builddash.backend.application.service;

import com.builddash.backend.api.dto.request.ReturnLineItemRequest;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.Return;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReturnService {

    Return createReturn(UUID userId, UUID orderId, ReturnReason reason, List<ReturnLineItemRequest> requestedItems, List<MultipartFile> photos);

    Return getReturn(UUID userId, List<String> roles, UUID returnId);

    Optional<Refund> getRefund(UUID returnId);

    Return approve(UUID returnId);

    Return schedulePickup(UUID returnId);

    Return pickUp(UUID returnId);

    Return passQc(UUID returnId, UUID userId, List<String> roles);

    Return reject(UUID returnId, UUID userId, List<String> roles);
}
