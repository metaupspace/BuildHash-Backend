package com.builddash.backend.domain.port;

import com.builddash.backend.domain.model.MarginRule;

import java.util.Optional;
import java.util.UUID;

public interface MarginRuleRepository {

    Optional<MarginRule> findByProductId(UUID productId);

    Optional<MarginRule> findByCategoryId(UUID categoryId);
}
