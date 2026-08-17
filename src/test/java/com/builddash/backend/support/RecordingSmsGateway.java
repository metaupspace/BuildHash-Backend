package com.builddash.backend.support;

import com.builddash.backend.domain.port.OtpSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only replacement for SmsOtpSender so integration tests can read back the OTP
 * that was "sent" instead of scraping log output.
 */
@Component
@Primary
public class RecordingSmsGateway implements OtpSender {

    private final Map<String, String> lastOtpByPhone = new ConcurrentHashMap<>();

    @Override
    public void send(String phone, String otp) {
        lastOtpByPhone.put(phone, otp);
    }

    public String lastOtpFor(String phone) {
        return lastOtpByPhone.get(phone);
    }
}
