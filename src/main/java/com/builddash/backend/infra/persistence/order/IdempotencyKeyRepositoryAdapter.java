package com.builddash.backend.infra.persistence.order;

import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepository {

    private final IdempotencyKeyJpaRepository jpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<UUID> findOrderId(String key, Instant createdAfter) {
        List<UUID> ids = jdbcTemplate.query(
                "SELECT order_id FROM idempotency_keys WHERE idempotency_key = ? AND created_at > ?",
                (rs, rowNum) -> (UUID) rs.getObject("order_id"),
                key, Timestamp.from(createdAfter)
        );
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    @Override
    public void save(String key, UUID orderId) {
        // Direct native INSERT ensures PostgreSQL raises a unique constraint violation
        // during concurrent double-submits (preventing JPA merge() from performing an UPDATE)
        jdbcTemplate.update(
                "INSERT INTO idempotency_keys (idempotency_key, order_id, created_at) VALUES (?, ?, now())",
                key, orderId
        );
    }

    @Override
    @Transactional
    public int deleteCreatedBefore(Instant cutoff) {
        return jpaRepository.deleteByCreatedAtBefore(cutoff);
    }
}
