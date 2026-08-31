package com.builddash.backend.infra.persistence;

import com.builddash.backend.application.service.CompanyMembershipService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.exception.LastOwnerProtectedException;
import com.builddash.backend.domain.exception.MemberAlreadyExistsException;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres proof of the membership constraints and the last-OWNER lock protocol
 * (ReturnConcurrencyJpaIT pattern): duplicate membership races resolve to exactly one
 * winner; concurrent removals of the last two OWNERs leave exactly one standing;
 * ownership transfer keeps an OWNER present at every commit point and demotes the old
 * OWNER to PROCUREMENT_MANAGER.
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

    @Test
    void concurrentDuplicateAddMember_exactlyOneRowCommits_loserGetsConflict() throws Exception {
        UUID memberUser = newUser();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        int[] wins = {0};
        int[] conflicts = {0};

        try {
            List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    try {
                        membershipService.addMember(companyId, ownerUserId, memberUser,
                                CompanyRole.VIEWER, List.of());
                        wins[0]++;
                    } catch (MemberAlreadyExistsException e) {
                        conflicts[0]++;
                    }
                    return null;
                }));
            }
            gate.countDown();
            for (java.util.concurrent.Future<Void> future : futures) {
                future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(wins[0]).isEqualTo(1);
        assertThat(conflicts[0]).isEqualTo(threads - 1);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM company_members WHERE company_id = ? AND user_id = ?",
                Integer.class, companyId, memberUser);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void concurrentRemovalOfLastTwoOwners_exactlyOneSucceeds_companyNeverStranded() throws Exception {
        UUID ownerAUser = newUser();
        UUID ownerBUser = newUser();
        UUID ownerA = membershipService.addMember(companyId, ownerUserId, ownerAUser,
                CompanyRole.OWNER, List.of()).id();
        UUID ownerB = membershipService.addMember(companyId, ownerUserId, ownerBUser,
                CompanyRole.OWNER, List.of()).id();

        // Founding owner leaves first: two OWNERs remain, allowed
        membershipService.removeMember(companyId, ownerUserId, ownerId);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        int[] successes = {0};
        int[] protectedRejections = {0};

        try {
            List<java.util.concurrent.Future<Void>> futures = List.of(
                    pool.submit(() -> {
                        gate.await();
                        try {
                            membershipService.removeMember(companyId, ownerAUser, ownerA);
                            successes[0]++;
                        } catch (LastOwnerProtectedException e) {
                            protectedRejections[0]++;
                        }
                        return null;
                    }),
                    pool.submit(() -> {
                        gate.await();
                        try {
                            membershipService.removeMember(companyId, ownerBUser, ownerB);
                            successes[0]++;
                        } catch (LastOwnerProtectedException e) {
                            protectedRejections[0]++;
                        }
                        return null;
                    }));
            gate.countDown();
            for (java.util.concurrent.Future<Void> future : futures) {
                future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes[0]).isEqualTo(1);
        assertThat(protectedRejections[0]).isEqualTo(1);
        Integer owners = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM company_members WHERE company_id = ? AND role = 'OWNER'",
                Integer.class, companyId);
        assertThat(owners).isEqualTo(1);
    }

    @Test
    void concurrentTransferAndRemoval_serializeWithoutCorruption() throws Exception {
        UUID targetUser = newUser();
        UUID targetId = membershipService.addMember(companyId, ownerUserId, targetUser,
                CompanyRole.SITE_SUPERVISOR, List.of()).id();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        final String[] outcome = {null, null};

        try {
            List<java.util.concurrent.Future<Void>> futures = List.of(
                    pool.submit(() -> {
                        gate.await();
                        try {
                            membershipService.transferOwnership(companyId, ownerUserId, targetId);
                            outcome[0] = "transferred";
                        } catch (Exception e) {
                            outcome[0] = "failed:" + e.getClass().getSimpleName();
                        }
                        return null;
                    }),
                    pool.submit(() -> {
                        gate.await();
                        try {
                            membershipService.removeMember(companyId, ownerUserId, targetId);
                            outcome[1] = "removed";
                        } catch (Exception e) {
                            outcome[1] = "failed:" + e.getClass().getSimpleName();
                        }
                        return null;
                    }));
            gate.countDown();
            for (java.util.concurrent.Future<Void> future : futures) {
                future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // Whichever wins, the invariant holds: exactly one OWNER remains
        Integer owners = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM company_members WHERE company_id = ? AND role = 'OWNER'",
                Integer.class, companyId);
        assertThat(owners).isEqualTo(1);
    }

    @Test
    void transferOwnership_oldOwnerBecomesProcurementManager_targetBecomesOwner() {
        UUID targetUser = newUser();
        UUID targetId = membershipService.addMember(companyId, ownerUserId, targetUser,
                CompanyRole.ACCOUNTANT, List.of()).id();

        membershipService.transferOwnership(companyId, ownerUserId, targetId);

        CompanyMember oldOwner = memberRepository.findById(ownerId).orElseThrow();
        CompanyMember newOwner = memberRepository.findById(targetId).orElseThrow();
        assertThat(oldOwner.role()).isEqualTo(CompanyRole.PROCUREMENT_MANAGER);
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
        UUID memberId = membershipService.addMember(companyId, ownerUserId, memberUser,
                CompanyRole.SITE_SUPERVISOR, List.of(site1, site2)).id();
        assertThat(assignmentRepository.findSiteIdsByMemberId(memberId))
                .containsExactlyInAnyOrder(site1, site2);

        membershipService.updateMember(companyId, ownerUserId, memberId, null, List.of(site2, site3));
        assertThat(assignmentRepository.findSiteIdsByMemberId(memberId))
                .containsExactlyInAnyOrder(site2, site3);

        jdbcTemplate.update("DELETE FROM company_members WHERE id = ?", memberId);
        assertThat(assignmentRepository.findSiteIdsByMemberId(memberId)).isEmpty();
    }
}
