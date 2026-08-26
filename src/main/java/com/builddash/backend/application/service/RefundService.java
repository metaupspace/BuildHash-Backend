package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Refund;

import java.util.UUID;

public interface RefundService {
    Refund initiateRefund(UUID returnId);
}
