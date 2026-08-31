package com.builddash.backend.infra.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client-IP derivation, extracted verbatim from AuthController.clientIp() (Checkpoint B
 * consolidation): first X-Forwarded-For entry (leftmost = original client) when present and
 * non-blank, else the socket's remote address. One home so login_events capture and
 * RateLimitFilter keying cannot drift apart. Leftmost-entry trust is the codebase's
 * established proxy posture; changing it is a deliberate security decision, not a refactor.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
