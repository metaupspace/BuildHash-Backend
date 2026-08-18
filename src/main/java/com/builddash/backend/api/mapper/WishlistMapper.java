package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.WishlistEntryResponse;
import com.builddash.backend.domain.model.WishlistEntry;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WishlistMapper {

    public WishlistEntryResponse toResponse(WishlistEntry entry) {
        return new WishlistEntryResponse(entry.getProductId(), entry.getCreatedAt());
    }

    public List<WishlistEntryResponse> toResponseList(List<WishlistEntry> entries) {
        return entries.stream().map(this::toResponse).toList();
    }
}
