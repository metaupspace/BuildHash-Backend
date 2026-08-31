package com.builddash.backend.infra.security;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.TokenClaims;
import com.builddash.backend.domain.port.TokenValidator;
import com.builddash.backend.domain.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Component
public class JwtTokenValidator implements TokenValidator {

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_DEVICE_ID = "deviceId";
    private static final String CLAIM_B2B = "b2b";

    private final JwtCodec codec;


    @Override
    public TokenClaims validate(String token, TokenType expectedType) {
        Claims claims = codec.decode(token);
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.name().equals(type)) {
            throw new UnauthorizedException("INVALID_TOKEN_TYPE", "Expected a " + expectedType.name() + " token");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String deviceIdClaim = claims.get(CLAIM_DEVICE_ID, String.class);
        UUID deviceId = deviceIdClaim == null ? null : UUID.fromString(deviceIdClaim);
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get(CLAIM_ROLES, List.class);

        return new TokenClaims(userId, deviceId, roles, parseB2bMemberships(claims.get(CLAIM_B2B)));
    }

    /**
     * Fail-safe parse of the optional "b2b" claim (decision 4 + 9-A safety rules):
     * missing/wrong-shape claim -> empty list; malformed entry -> that entry is
     * skipped; unknown role -> entry skipped; malformed UUID -> entry skipped;
     * missing/invalid sites list -> empty sites. Malformed B2B content can never
     * grant access (it only ever removes context) and never fails authentication —
     * the token's signature already proved who the caller is; money-path operations
     * re-check membership in the database regardless.
     *
     * Unknown-but-syntactically-valid company/site UUIDs intentionally pass through:
     * existence is a database question answered at the critical-operation re-check.
     */
    private static List<B2bMembership> parseB2bMemberships(Object claim) {
        if (!(claim instanceof List<?> entries)) {
            return List.of();
        }
        List<B2bMembership> memberships = new ArrayList<>();
        for (Object entry : entries) {
            if (!(entry instanceof java.util.Map<?, ?> fields)) {
                continue;
            }
            Object cid = fields.get("cid");
            Object role = fields.get("role");
            if (!(cid instanceof String cidText) || !(role instanceof String roleText)) {
                continue;
            }
            UUID companyId;
            try {
                companyId = UUID.fromString(cidText);
            } catch (IllegalArgumentException e) {
                continue;
            }
            CompanyRole parsedRole;
            try {
                parsedRole = CompanyRole.valueOf(roleText);
            } catch (IllegalArgumentException e) {
                continue;
            }
            memberships.add(new B2bMembership(companyId, parsedRole, parseSiteIds(fields.get("sites"))));
        }
        return List.copyOf(memberships);
    }

    private static List<UUID> parseSiteIds(Object sites) {
        if (!(sites instanceof List<?> siteEntries)) {
            return List.of();
        }
        List<UUID> siteIds = new ArrayList<>();
        for (Object site : siteEntries) {
            if (site instanceof String text) {
                try {
                    siteIds.add(UUID.fromString(text));
                } catch (IllegalArgumentException e) {
                    // skip malformed site id — an unparseable id scopes nothing
                }
            }
        }
        return List.copyOf(siteIds);
    }
}
