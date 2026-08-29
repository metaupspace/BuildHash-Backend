package com.builddash.backend.domain.port;

import com.builddash.backend.domain.enums.NotificationEventType;

import java.util.UUID;

/**
 * OCP: the SMS notification channel is a new implementation of this interface. Deliberately
 * NOT an extension of OtpSender — that port's send(phone, otp) signature is OTP-specific and
 * reusable only by inheritance in name (fact-finding Fact 2, PLAN_PHASE7 Section 1).
 */
public interface SmsNotificationSender {

    void send(String recipient, NotificationEventType eventType, UUID referenceId);
}
