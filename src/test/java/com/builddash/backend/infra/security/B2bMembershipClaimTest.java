package com.builddash.backend.infra.security;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.TokenType;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.IssuedToken;
import com.builddash.backend.domain.model.TokenClaims;
import com.builddash.backend.infra.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The "b2b" claim contract (decision 4 + 9-A safety rules). Issuer/validator are plain
 * objects over JwtProperties, so this is a pure unit round-trip — no Spring context.
 * Authorities behavior (B2B roles never become ROLE_*) is covered by the filter
 * assertions at the bottom, mirroring JwtAuthenticationFilter's mapping.
 */
class B2bMembershipClaimTest {

    private JwtTokenIssuer issuer;
    private JwtTokenValidator validator;

    private static final UUID USER = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-0123456789-0123456789");
        properties.setIssuer("builddash-unit");
        properties.setAccessTokenTtlMinutes(5);
        properties.setRefreshTokenTtlDays(1);
        properties.setGuestTokenTtlHours(1);
        JwtCodec codec = new JwtCodec(properties);
        issuer = new JwtTokenIssuer(codec, properties);
        validator = new JwtTokenValidator(codec);
    }

    private List<B2bMembership> parse(IssuedToken token) {
        return validator.validate(token.token(), TokenType.ACCESS).b2bMemberships();
    }

    // Custom-claim injection: these tokens are built through the same codec, with claim
    // payloads the issuer itself would never produce — exactly what a malformed or
    // hand-crafted-but-signed token looks like to the parser.
    private IssuedToken tokenWithRawB2bClaim(Object rawClaim) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-0123456789-0123456789");
        properties.setIssuer("builddash-unit");
        properties.setAccessTokenTtlMinutes(5);
        JwtCodec codec = new JwtCodec(properties);
        return codec.encode(USER, 300, java.util.Map.of(
                "typ", TokenType.ACCESS.name(),
                "roles", List.of("USER"),
                "deviceId", DEVICE.toString(),
                "b2b", rawClaim));
    }

    @Test
    void missingB2bClaim_parsesToEmptyMemberships() {
        // Issued via the legacy-signature path: no b2b claim at all
        IssuedToken token = issuer.issueAccessToken(USER, DEVICE, List.of("USER"));
        assertThat(parse(token)).isEmpty();
    }

    @Test
    void validB2bClaim_roundTrips() {
        UUID company = UUID.randomUUID();
        UUID site = UUID.randomUUID();
        IssuedToken token = issuer.issueAccessToken(USER, DEVICE, List.of("USER"),
                List.of(new B2bMembership(company, CompanyRole.SITE_SUPERVISOR, List.of(site))));

        List<B2bMembership> parsed = parse(token);
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).companyId()).isEqualTo(company);
        assertThat(parsed.get(0).role()).isEqualTo(CompanyRole.SITE_SUPERVISOR);
        assertThat(parsed.get(0).siteIds()).containsExactly(site);
    }

    @Test
    void emptyB2bClaim_parsesToEmptyMemberships() {
        IssuedToken token = issuer.issueAccessToken(USER, DEVICE, List.of("USER"), List.of());
        assertThat(parse(token)).isEmpty();
    }

    @Test
    void malformedB2bClaim_wrongTopLevelShape_degradesToEmpty() {
        assertThat(parse(tokenWithRawB2bClaim("not-a-list"))).isEmpty();
        assertThat(parse(tokenWithRawB2bClaim(42))).isEmpty();
    }

    @Test
    void malformedB2bClaim_nonMapEntriesAreSkipped() {
        Object claim = List.of("junk-string", 7,
                java.util.Map.of("cid", UUID.randomUUID().toString(), "role", "VIEWER"));
        assertThat(parse(tokenWithRawB2bClaim(claim))).hasSize(1);
    }

    @Test
    void unknownRoleValue_entrySkipped_includingOldVocabulary() {
        // "SUPERUSER" never existed; "ADMIN"/"BUYER"/"APPROVER" existed in 9-A tokens —
        // after the 9-A.1 correction they are unknown values and must skip safely,
        // so a stale token gains no membership context.
        Object claim = List.of(
                java.util.Map.of("cid", UUID.randomUUID().toString(), "role", "SUPERUSER"),
                java.util.Map.of("cid", UUID.randomUUID().toString(), "role", "ADMIN"),
                java.util.Map.of("cid", UUID.randomUUID().toString(), "role", "BUYER"),
                java.util.Map.of("cid", UUID.randomUUID().toString(), "role", "VIEWER"));
        List<B2bMembership> parsed = parse(tokenWithRawB2bClaim(claim));
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).role()).isEqualTo(CompanyRole.VIEWER);
    }

    @Test
    void malformedCompanyUuid_entrySkipped() {
        Object claim = List.of(
                java.util.Map.of("cid", "not-a-uuid", "role", "ACCOUNTANT"),
                java.util.Map.of("cid", UUID.randomUUID().toString(), "role", "VIEWER"));
        assertThat(parse(tokenWithRawB2bClaim(claim))).hasSize(1);
    }

    @Test
    void malformedSiteUuid_skippedForThatMembership() {
        UUID company = UUID.randomUUID();
        UUID goodSite = UUID.randomUUID();
        Object claim = List.of(java.util.Map.of(
                "cid", company.toString(), "role", "PROCUREMENT_MANAGER",
                "sites", List.of("bad-site-id", goodSite.toString(), 5)));
        List<B2bMembership> parsed = parse(tokenWithRawB2bClaim(claim));
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).siteIds()).containsExactly(goodSite);
    }

    @Test
    void unknownSiteUuid_survivesParsing() {
        // Syntactically valid ids pass through: existence is a DB question answered at
        // the critical-operation re-check, not by the parser.
        UUID unknownSite = UUID.randomUUID();
        IssuedToken token = issuer.issueAccessToken(USER, DEVICE, List.of("USER"),
                List.of(new B2bMembership(UUID.randomUUID(), CompanyRole.VIEWER, List.of(unknownSite))));
        assertThat(parse(token).get(0).siteIds()).containsExactly(unknownSite);
    }

    @Test
    void multipleCompanyMemberships_allRoundTrip() {
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        IssuedToken token = issuer.issueAccessToken(USER, DEVICE, List.of("USER"),
                List.of(new B2bMembership(c1, CompanyRole.OWNER, List.of()),
                        new B2bMembership(c2, CompanyRole.VIEWER, List.of())));

        List<B2bMembership> parsed = parse(token);
        assertThat(parsed).hasSize(2);
        assertThat(parsed).extracting(B2bMembership::companyId).containsExactlyInAnyOrder(c1, c2);
    }

    @Test
    void legacyToken_issuedBeforeB2bClaimExisted_stillValidates() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-0123456789-0123456789");
        properties.setIssuer("builddash-unit");
        properties.setAccessTokenTtlMinutes(5);
        JwtCodec codec = new JwtCodec(properties);
        IssuedToken legacy = codec.encode(USER, 300, java.util.Map.of(
                "typ", TokenType.ACCESS.name(),
                "roles", List.of("USER"),
                "deviceId", DEVICE.toString()));

        TokenClaims claims = validator.validate(legacy.token(), TokenType.ACCESS);
        assertThat(claims.b2bMemberships()).isEmpty();
        assertThat(claims.roles()).containsExactly("USER");
    }

    @Test
    void b2bRoles_neverBecomeSpringAuthorities() {
        // Mirror of JwtAuthenticationFilter's authority mapping: it derives authorities
        // from claims.roles() ONLY. A token carrying B2B OWNER must not carry ROLE_OWNER.
        IssuedToken token = issuer.issueAccessToken(USER, DEVICE, List.of("USER"),
                List.of(new B2bMembership(UUID.randomUUID(), CompanyRole.OWNER, List.of())));
        TokenClaims claims = validator.validate(token.token(), TokenType.ACCESS);

        List<String> authorities = claims.roles().stream().map("ROLE_"::concat).toList();
        assertThat(authorities).containsExactly("ROLE_USER");
        assertThat(authorities).doesNotContain("ROLE_OWNER", "ROLE_ADMIN", "ROLE_BUYER", "ROLE_APPROVER");
    }
}
