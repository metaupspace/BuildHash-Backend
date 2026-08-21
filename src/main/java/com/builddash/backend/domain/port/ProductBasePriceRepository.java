package com.builddash.backend.domain.port;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ProductBasePriceRepository {

    Optional<BigDecimal> findByProductId(UUID productId);

    BigDecimal save(UUID productId, BigDecimal price);
}
