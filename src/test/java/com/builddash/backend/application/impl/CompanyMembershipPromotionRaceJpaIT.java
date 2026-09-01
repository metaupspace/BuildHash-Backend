package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.CompanyMembershipService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.builddash.backend.support.ApprovalTestFixtures.grantPermission;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCompany;
import static com.builddash.backend.support.ApprovalTestFixtures.seedMember;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;

/**
 * H0.3 on real Postgres: MEMBER_MANAGE is delegable member administration, but the
 * OWNER crown is not — only an OWNER assigns OWNER, nobody changes their own role,
 * and racing promotions cannot manufacture a second crown or strand the company.
 */
class CompanyMembershipPromotionRaceJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CompanyMembershipService membershipService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID companyId;
    private UUID ownerUserId;
    private UUID ownerMemberId;

    @BeforeEach
    void setUp() {
        companyId = seedCompany(jdbc, "CrownRaceCo");
        ownerUserId = seedUser(jdbc);
        ownerMemberId = seedMember(jdbc, companyId, ownerUserId, "OWNER", null);
    }

    @Test
    void memberManageHolder_cannotAddOrPromoteOwner() {
        UUID manager = seedUser(jdbc);
        seedMember(jdbc, companyId, manager, "PROCUREMENT_MANAGER", null);
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "MEMBER_MANAGE");
        UUID target = seedUser(jdbc);
        UUID targetMemberId = seedMember(jdbc, companyId, target, "PROCUREMENT_MANAGER", null);

        assertThatThrownBy(() -> membershipService.addMember(
                companyId, manager, UUID.randomUUID(), CompanyRole.OWNER, null))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        assertThatThrownBy(() -> membershipService.updateMember(
                companyId, manager, targetMemberId, CompanyRole.OWNER, null))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        assertThat(ownerCount()).isEqualTo(1);
    }

    @Test
    void memberManageHolder_cannotChangeOwnRole() {
        UUID manager = seedUser(jdbc);
        UUID managerMemberId = seedMember(jdbc, companyId, manager, "PROCUREMENT_MANAGER", null);
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "MEMBER_MANAGE");

        assertThatThrownBy(() -> membershipService.updateMember(
                companyId, manager, managerMemberId, CompanyRole.SITE_SUPERVISOR, null))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_ROLE_CHANGE");

        assertThat(roleOf(managerMemberId)).isEqualTo("PROCUREMENT_MANAGER");
    }

    @Test
    void ownerPromotesMember_toOwner() {
        UUID target = seedUser(jdbc);
        UUID targetMemberId = seedMember(jdbc, companyId, target, "PROCUREMENT_MANAGER", null);

        CompanyMember promoted = membershipService.updateMember(
                companyId, ownerUserId, targetMemberId, CompanyRole.OWNER, null);

        assertThat(promoted.role()).isEqualTo(CompanyRole.OWNER);
        assertThat(ownerCount()).isEqualTo(2);
    }

    @Test
    void concurrentNonOwnerPromotions_bothRejected_invariantHeld() throws Exception {
        UUID m1 = seedUser(jdbc);
        UUID m1MemberId = seedMember(jdbc, companyId, m1, "PROCUREMENT_MANAGER", null);
        UUID m2 = seedUser(jdbc);
        UUID m2MemberId = seedMember(jdbc, companyId, m2, "PROCUREMENT_MANAGER", null);
        UUID n = seedUser(jdbc);
        UUID nMemberId = seedMember(jdbc, companyId, n, "PROCUREMENT_MANAGER", null);
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "MEMBER_MANAGE");

        // m1 tries to crown itself, m2 tries to crown n — both under the company lock
        Callable<Object> selfCrown = unchecked(() -> membershipService.updateMember(
                companyId, m1, m1MemberId, CompanyRole.OWNER, null));
        Callable<Object> crownOther = unchecked(() -> membershipService.updateMember(
                companyId, m2, nMemberId, CompanyRole.OWNER, null));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<Object> f1 = pool.submit(gated(start, selfCrown));
            Future<Object> f2 = pool.submit(gated(start, crownOther));

            for (Future<Object> f : List.of(f1, f2)) {
                assertThatThrownBy(f::get).hasCauseInstanceOf(ForbiddenException.class);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(ownerCount())
                .as("racing MEMBER_MANAGE holders must not manufacture an OWNER")
                .isEqualTo(1);
    }

    @Test
    void concurrentDemoteAndPromote_lastOwnerInvariantHeld() throws Exception {
        UUID manager = seedUser(jdbc);
        seedMember(jdbc, companyId, manager, "PROCUREMENT_MANAGER", null);
        UUID n = seedUser(jdbc);
        UUID nMemberId = seedMember(jdbc, companyId, n, "PROCUREMENT_MANAGER", null);
        grantPermission(jdbc, companyId, "PROCUREMENT_MANAGER", "MEMBER_MANAGE");

        // Owner tries to demote itself (H0.3 forbids self role-change outright — even
        // stronger than the invariant) while a MEMBER_MANAGE holder tries to crown n.
        // Whatever interleaves, the company keeps its OWNER and gains no illegitimate crown.
        Callable<Object> selfDemote = unchecked(() -> membershipService.updateMember(
                companyId, ownerUserId, ownerMemberId, CompanyRole.PROCUREMENT_MANAGER, null));
        Callable<Object> crownN = unchecked(() -> membershipService.updateMember(
                companyId, manager, nMemberId, CompanyRole.OWNER, null));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<Object> f1 = pool.submit(gated(start, selfDemote));
            Future<Object> f2 = pool.submit(gated(start, crownN));

            // Self-change rejected (SELF_ROLE_CHANGE), crown by non-OWNER rejected.
            assertThatThrownBy(f1::get).hasCauseInstanceOf(ForbiddenException.class);
            assertThatThrownBy(f2::get).hasCauseInstanceOf(ForbiddenException.class);
        } finally {
            pool.shutdownNow();
        }

        assertThat(ownerCount()).isEqualTo(1);
        assertThat(roleOf(ownerMemberId)).isEqualTo("OWNER");
    }

    private int ownerCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM company_members WHERE company_id = ? AND role = 'OWNER'",
                Integer.class, companyId);
    }

    private String roleOf(UUID memberId) {
        return jdbc.queryForObject(
                "SELECT role FROM company_members WHERE id = ?", String.class, memberId);
    }

    private static Callable<Object> unchecked(FailingCall call) {
        return () -> {
            call.run();
            return null;
        };
    }

    private interface FailingCall {
        void run() throws Exception;
    }

    private static Callable<Object> gated(CyclicBarrier barrier, Callable<Object> task) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return task.call();
        };
    }
}
