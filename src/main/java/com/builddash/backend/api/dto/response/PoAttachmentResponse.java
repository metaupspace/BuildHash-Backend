package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.PoAttachment;

import java.time.Instant;
import java.util.UUID;

public record PoAttachmentResponse(
        UUID id,
        UUID orderId,
        String status,
        String contentType,
        int byteSize,
        UUID uploadedBy,
        Instant createdAt
) {

    public static PoAttachmentResponse from(PoAttachment attachment) {
        return new PoAttachmentResponse(attachment.id(), attachment.orderId(),
                attachment.status().name(), attachment.contentType(), attachment.byteSize(),
                attachment.uploadedBy(), attachment.createdAt());
    }
}
