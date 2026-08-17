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

    static {
        try {
            REDIS_SERVER = RedisServer.newRedisServer();
            REDIS_SERVER.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start jedis-mock Redis server", e);
        }

        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
        POSTGRES.start();
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
