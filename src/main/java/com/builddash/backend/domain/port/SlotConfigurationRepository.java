package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.SlotConfiguration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlotConfigurationRepository {
    List<SlotConfiguration> findAllActive();
    Optional<SlotConfiguration> findById(UUID id);
}
