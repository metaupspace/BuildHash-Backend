package com.builddash.backend.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class WishlistEntry {

    private UUID id;
    private UUID userId;
    private UUID productId;
    private Instant createdAt;
}
