package com.builddash.backend.domain.port;

/**
 * OCP: the async transport (RabbitMQ today, could be SQS/Kafka later) is behind this interface
 * — OtpSendService never changes to swap it. Kept separate from OtpSender (the actual SMS/email/
 * WhatsApp channel): this interface's only job is getting the message onto a queue reliably,
 * not delivering it.
 */
public interface OtpDispatchQueue {

    void enqueue(String phone, String otp);
}
