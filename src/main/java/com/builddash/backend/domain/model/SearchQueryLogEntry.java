package com.builddash.backend.domain.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class SearchQueryLogEntry {

    private UUID id;

    /** Null for anonymous/guest searches — they still count toward trending, just not "my history". */
    private UUID userId;

    private String queryText;
    private String lang;
    private Instant createdAt;
}
