package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.WishlistEntry;

import java.util.List;
import java.util.UUID;

public interface WishlistReader {

    List<WishlistEntry> list(UUID userId);
}
