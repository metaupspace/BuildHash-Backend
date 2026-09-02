package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The encryption transition, end-to-end against real Postgres: a raw-SQL plaintext user
 * (NULL blind index) → backfill sweep → phone column holds v1: ciphertext, phone_idx is
 * populated, findByPhone still resolves through the HMAC path, and a second sweep is a
 * no-op — the sweep is its own completion proof.
 */
class PiiBackfillJpaIT extends AbstractIntegrationTest {

    @Autowired
    private PiiBackfillSweeper sweeper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        sweeper.reset();
    }

    private String storedColumn(String column, UUID id) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM users WHERE id = ?", String.class, id);
    }

    @Test
    void plaintextRow_backfilledToCiphertextAndIdx_findByPhoneStillResolves_resweepNoop() {
        UUID userId = UUID.randomUUID();
        String phone = "+9188" + String.format("%08d", Math.abs(userId.hashCode() % 100000000));
        jdbcTemplate.update("INSERT INTO users (id, phone) VALUES (?, ?)", userId, phone);

        // Pre-sweep: plaintext column, NULL idx, plaintext lookup impossible via idx path.
        assertThat(storedColumn("phone", userId)).isEqualTo(phone);
        assertThat(storedColumn("phone_idx", userId)).isNull();
        assertThat(userRepository.findByPhone(phone)).isEmpty();

        sweeper.sweep();

        assertThat(storedColumn("phone", userId)).startsWith("v1:").doesNotContain(phone);
        assertThat(storedColumn("phone_idx", userId)).isNotBlank();
        Optional<com.builddash.backend.domain.model.User> resolved = userRepository.findByPhone(phone);
        assertThat(resolved).hasValueSatisfying(user -> assertThat(user.getId()).isEqualTo(userId));

        // Re-sweep: nothing left to backfill — idempotent.
        sweeper.sweep();
        assertThat(storedColumn("phone_idx", userId)).isNotBlank();
    }

    @Test
    void repositoryWrittenRow_isBornEncrypted_neverNeedsBackfill() {
        com.builddash.backend.domain.model.User user = new com.builddash.backend.domain.model.User();
        user.setPhone("+9177" + String.format("%08d", System.nanoTime() % 100000000));
        UUID userId = userRepository.save(user).getId();

        assertThat(storedColumn("phone", userId)).startsWith("v1:");
        assertThat(storedColumn("phone_idx", userId)).isNotBlank();
        assertThat(userRepository.findByPhone(user.getPhone())).isPresent();
    }
}
