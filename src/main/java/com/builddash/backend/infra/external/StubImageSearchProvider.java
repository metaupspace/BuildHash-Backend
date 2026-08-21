package com.builddash.backend.infra.external;

import com.builddash.backend.domain.port.ImageSearchProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Stub for Phase 1 (Open Question #2, resolved: third-party vision/matching API, low-stakes
 * since ImageSearchProvider isolates the choice). Not profile-gated like SmsOtpSender — no
 * real vendor is expected imminently even in prod, so there's no "swap before going live" step
 * to enforce yet.
 */
@Component
public class StubImageSearchProvider implements ImageSearchProvider {

    @Override
    public List<UUID> matchByImage(byte[] image) {
        return List.of();
    }
}
