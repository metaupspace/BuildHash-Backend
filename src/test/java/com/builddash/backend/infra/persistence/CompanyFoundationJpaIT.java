package com.builddash.backend.infra.persistence;

import com.builddash.backend.application.service.CompanyMembershipService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.LastAdminProtectedException;
import com.builddash.backend.domain.exception.MemberAlreadyExistsException;
import com.builddash.backend.domain.model.B2bMembership;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres proof of the V25 membership constraints and the last-admin lock
 * protocol (ReturnConcurrencyJpaIT pattern): duplicate membership races resolve to
 * exactly one winner; concurrent removals of the last two admins leave exactly one
 * standing; owner transfer keeps an OWNER present at every commit point.
 */
class CompanyFoundationJpaIT extends AbstractIntegrationTest {

    @Autowired
    private CompanyMembershipService membershipService;

    @Autowired
    private CompanyMemberRepository memberRepository;

    @Autowired
    private CompanySiteAssignmentRepository assignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID ownerId;
    private final UUID ownerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO companies (id, name) VALUES (?, 'Acme')", companyId);
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", ownerUserId);
        jdbcTemplate.update("INSERT INTO company_members (id, company_id, user_id, role) VALUES (?, ?, ?, 'OWNER')",
                ownerId, companyId, ownerUserId);
    }

    private UUID newUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        return userId;
    }

    private List<B2bMembership> claim(CompanyRole role) {
        return List.of(new B2bMembership(companyId, role, List.of()));
    }

    @Test
    void concurrentDuplicateAddMember_exactlyOneRowCommits_loserGetsConflict() throws Exception {
        UUID memberUser = newUser();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        try {
            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    try {
                        membershipService.addMember(companyId, ownerUserId, claim(CompanyRole.OWNER),
                                memberUser, CompanyRole.BUYER, List.of());
                        wins.incrementAndGet();
                    } catch (MemberAlreadyExistsException e) {
                        conflicts.incrementAndGet();
                    }
                    return null;
                }));
            }
            gate.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(wins.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM company_members WHERE company_id = ? AND user_id = ?",
                Integer.class, companyId, memberUser);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void concurrentRemovalOfLastTwoAdmins_exactlyOneSucceeds_companyNeverStranded() throws Exception {
        UUID adminAUser = newUser();
        UUID adminBUser = newUser();
        UUID adminA = membershipService.addMember(companyId, ownerUserId, claim(CompanyRole.OWNER),
                adminAUser, CompanyRole.ADMIN, List.of()).id();
        UUID adminB = membershipService.addMember(companyId, ownerUserId, claim(CompanyRole.OWNER),
                adminBUser, CompanyRole.ADMIN, List.of()).id();

        // Owner leaves first: two admins remain, allowed
        membershipService.removeMember(companyId, ownerUserId, claim(CompanyRole.OWNER), ownerId);
        List<B2bMembership> ownerClaim = claim(CompanyRole.ADMIN);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger protectedRejections = new AtomicInteger();

        try {
            List<Future<Void>> futures = List.of(
                    pool.submit(() -> {
                        gate.await();
                        try {
                            membershipService.removeMember(companyId, adminAUser, ownerClaim, adminA);
                            successes.incrementAndGet();
                        } catch (LastAdminProtectedException e) {
                            protectedRejections.incrementAndGet();
                        }
                        return null;
                    }),
                    pool.submit(() -> {
                        gate.await();
                        try {
                            membershipService.removeMember(companyId, adminBUser, ownerClaim, adminB);
                            successes.incrementAndGet();
                        } catch (LastAdminProtectedException e) {
                            protectedRejections.incrementAndGet();
                        }
                        return null;
                    }));
            gate.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(protectedRejections.get()).isEqualTo(1);
        Integer admins = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM company_members WHERE company_id = ? AND role IN ('OWNER','ADMIN')",
                Integer.class, companyId);
        assertThat(admins).isEqualTo(1); // exactly one admin survives
    }

    @Test
    void transferOwnership_oldOwnerBecomesAdmin_targetBecomesOwner() {
        UUID targetUser = newUser();
        UUID targetId = membershipService.addMember(companyId, ownerUserId, claim(CompanyRole.OWNER),
                targetUser, CompanyRole.APPROVER, List.of()).id();

        membershipService.transferOwnership(companyId, ownerUserId, claim(CompanyRole.OWNER), targetId);

        CompanyMember oldOwner = memberRepository.findById(ownerId).orElseThrow();
        CompanyMember newOwner = memberRepository.findById(targetId).orElseThrow();
        assertThat(oldOwner.role()).isEqualTo(CompanyRole.ADMIN);
        assertThat(newOwner.role()).isEqualTo(CompanyRole.OWNER);
    }

    @Test
    void assignmentReplace_swapsSiteSet_andCascadesOnMemberDelete() {
        UUID site1 = UUID.randomUUID();
        UUID site2 = UUID.randomUUID();
        UUID site3 = UUID.randomUUID();
        for (UUID site : List.of(site1, site2, site3)) {
            jdbcTemplate.update("INSERT INTO company_sites (id, company_id, name) VALUES (?, ?, ?)",
                    site, companyId, "Site " + site);
        }

        UUID memberUser = newUser();
        UUID memberId = membershipService.addMember(companyId, ownerUserId, claim(CompanyRole.OWNER),
                memberUser, CompanyRole.APPROVER, List.of(site1, site2)).id();
        assertThat(assignmentRepository.findSiteIdsByMemberId(memberId))
                .containsExactlyInAnyOrder(site1, site2);

        // Replace: site1 out, site3 in
        membershipService.updateMember(companyId, ownerUserId, claim(CompanyRole.OWNER),
                memberId, null, List.of(site2, site3));
        assertThat(assignmentRepository.findSiteIdsByMemberId(memberId))
                .containsExactlyInAnyOrder(site2, site3);

        // Member delete cascades assignments away (V25 ON DELETE CASCADE)
        jdbcTemplate.update("DELETE FROM company_members WHERE id = ?", memberId);
        assertThat(assignmentRepository.findSiteIdsByMemberId(memberId)).isEmpty();
    }
}
