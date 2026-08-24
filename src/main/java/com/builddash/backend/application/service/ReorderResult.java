package com.builddash.backend.application.service;

import java.util.UUID;

public record ReorderResult(UUID cartId, String message) {}
