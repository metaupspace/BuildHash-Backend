package com.builddash.backend.application.service;

import com.builddash.backend.domain.model.Order;

public record OrderResult(Order order, String paymentUrl) {}
