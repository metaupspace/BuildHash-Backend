package com.builddash.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fppt.jedismock.RedisServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requires Docker: Postgres is a real Testcontainers instance (schema created by the actual
 * Flyway migrations under db/migration/postgresql, same as production — no H2 mirror to keep
 * in sync), and Redis is jedis-mock — a pure-Java in-memory RESP server, so the real
 * StringRedisTemplate/Lettuce client connects unchanged. Both are started once per JVM
 * (singleton pattern) and shared across every subclass instead of being restarted per test class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final RedisServer REDIS_SERVER;
    private static final PostgreSQLContainer<?> POSTGRES;
    private static final MinIOContainer MINIO;

    static {
        // Test-only PII master key: the production yaml has NO default by design (fail
        // closed, PLAN_PHASE8 decision 2), so every IT context must supply one explicitly.
        System.setProperty("security.pii.master-key",
                java.util.Base64.getEncoder().encodeToString(new byte[32]));

        // Test-wide raised rate limits (Checkpoint B): RateLimitFilter fronts EVERY request
        // and all ITs share 127.0.0.1's budget — without headroom, unrelated /search ITs
        // would trip 429s. RateLimitIT overrides these via @DynamicPropertySource (higher
        // precedence, own cached context) to exercise the real configured limits.
        System.setProperty("security.rate-limit.rules.search.limit", "10000");
        System.setProperty("security.rate-limit.rules.google.limit", "10000");

        try {
            REDIS_SERVER = RedisServer.newRedisServer();
            REDIS_SERVER.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start jedis-mock Redis server", e);
        }

        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();

        MINIO = new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");
        MINIO.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", REDIS_SERVER::getBindPort);
    }

    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("storage.s3.endpoint", MINIO::getS3URL);
        registry.add("storage.s3.access-key", MINIO::getUserName);
        registry.add("storage.s3.secret-key", MINIO::getPassword);
        registry.add("storage.s3.bucket", () -> "test-bucket");
        registry.add("storage.s3.region", () -> "ap-south-1");
        registry.add("storage.s3.path-style-access", () -> "true");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RecordingSmsGateway smsGateway;

    /**
     * Drives a full OTP send + verify round-trip and returns the parsed AuthTokensResponse body,
     * so controller tests for user/device/login-history don't each have to re-implement login.
     */
    protected JsonNode loginViaOtp(String phone, String deviceFingerprint) throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());

        String otp = smsGateway.lastOtpFor(phone);

        MvcResult result = mockMvc.perform(post("/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"otp\":\"" + otp + "\",\"deviceFingerprint\":\"" + deviceFingerprint + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
