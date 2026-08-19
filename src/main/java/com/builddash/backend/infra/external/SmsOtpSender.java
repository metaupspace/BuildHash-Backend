package com.builddash.backend.infra.external;

import com.builddash.backend.domain.port.OtpSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Stub gateway for Phase 0 — no real SMS provider is wired up yet, so the OTP is only logged.
 * Restricted to non-prod profiles: swap in a real provider (active under "prod") before going live,
 * so OTPs never end up in production logs.
 * Swap in a real provider by adding another OtpSender implementation, per OCP.
 */
@Component
@Profile("!prod")
@Slf4j
public class SmsOtpSender implements OtpSender {

    @Override
    public void send(String phone, String otp) {
        log.info(">>> [DEV OTP] phone={} otp={}", phone, otp);
    }
}
