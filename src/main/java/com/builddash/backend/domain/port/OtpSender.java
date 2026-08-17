package com.builddash.backend.domain.port;

/**
 * OCP: the delivery channel (SMS today, email/WhatsApp later) is a new implementation of this
 * interface — OtpSendService never changes to add one.
 */
public interface OtpSender {

    void send(String phone, String otp);
}
