package com.builddash.backend.infra.messaging;

/**
 * Wire payload for the async OTP dispatch queue. Kept separate from OtpSender's plain
 * (phone, otp) parameters since a queue message may need envelope fields later (message id,
 * retry count) without touching the OtpSender contract.
 */
public record OtpDispatchMessage(String phone, String otp) {
}
