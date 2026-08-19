package com.builddash.backend.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class NotifyMeSubscription {

    private UUID id;
    private UUID productId;
    private UUID userId;
    private Instant createdAt;
}
