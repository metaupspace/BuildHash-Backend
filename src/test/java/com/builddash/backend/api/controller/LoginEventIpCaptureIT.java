package com.builddash.backend.api.controller;

import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Regression proof for the ClientIpResolver consolidation (Checkpoint B): AuthController's
 * inline X-Forwarded-For logic moved to infra/security — login_events IP capture must record
 * exactly what the old inline method produced. Both header cases exercised against a real
 * login, asserting the PERSISTED ip_address, not resolver return values.
 */
class LoginEventIpCaptureIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    private void login(String phone, String forwardedFor) throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        var request = post("/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"otp\":\"" + smsGateway.lastOtpFor(phone) + "\"}");
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        mockMvc.perform(request)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }

    @Test
    void forwardedForPresent_persistsLeftmostEntry() throws Exception {
        String phone = "+917700000111";
        login(phone, "203.0.113.9, 10.0.0.1");   // old inline logic: split(",")[0].trim()

        var user = userRepository.findByPhone(phone).orElseThrow();
        String persistedIp = jdbcTemplate.queryForObject(
                "SELECT ip_address FROM login_events WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class, user.getId());
        assertThat(persistedIp).isEqualTo("203.0.113.9");
    }

    @Test
    void forwardedForAbsent_persistsRemoteAddr() throws Exception {
        String phone = "+917700000112";
        login(phone, null);                       // MockMvc default remoteAddr is 127.0.0.1

        var user = userRepository.findByPhone(phone).orElseThrow();
        String persistedIp = jdbcTemplate.queryForObject(
                "SELECT ip_address FROM login_events WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class, user.getId());
        assertThat(persistedIp).isEqualTo("127.0.0.1");
    }
}
