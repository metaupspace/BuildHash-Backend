package com.builddash.backend.api;

import com.builddash.backend.application.service.ApprovalGateService;
import com.builddash.backend.domain.enums.ApprovalMatchRule;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.ApprovalPolicy;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.ApprovalPolicyRepository;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.ApprovalTestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.builddash.backend.support.ApprovalTestFixtures.grantPermission;
import static com.builddash.backend.support.ApprovalTestFixtures.seedCompany;
import static com.builddash.backend.support.ApprovalTestFixtures.seedMember;
import static com.builddash.backend.support.ApprovalTestFixtures.seedPolicy;
import static com.builddash.backend.support.ApprovalTestFixtures.seedProductWithCategory;
import static com.builddash.backend.support.ApprovalTestFixtures.seedSite;
import static com.builddash.backend.support.ApprovalTestFixtures.seedUser;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 9-D policy matching on real Postgres: OR semantics over the persisted policy row
 * (native arrays round-tripped by the adapter), snapshot immutability across policy
 * replacement, and no-policy = no gate.
 */
class ApprovalPolicyMatchJpaIT extends AbstractIntegrationTest {

    @Autowired
    private ApprovalGateService gateService;
    @Autowired
    private ApprovalPolicyRepository policyRepository;
    @Autowired
    private ApprovalRequestRepository requestRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID companyId;
    private UUID siteId;
    private UUID productId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        companyId = seedCompany(jdbc, "PolicyMatchCo");
        siteId = seedSite(jdbc, companyId, "HQ", true);
        UUID[] pc = seedProductWithCategory(jdbc);
        productId = pc[0];
        categoryId = pc[1];
    }

    private ApprovalGateService.GateDecision evaluate(BigDecimal threshold, UUID[] categories,
                                                      UUID[] sites, BigDecimal total, UUID orderSite) {
        jdbc.update("DELETE FROM company_approval_policies WHERE company_id = ?", companyId);
        seedPolicy(jdbc, companyId, threshold, categories, sites,
                new String[]{"PROCUREMENT_MANAGER", "OWNER"}, 24, 1);
        return gateService.evaluate(companyId, total, List.of(productId), orderSite);
    }

    @Test
    void noPolicyRow_notGated() {
        assertThat(gateService.evaluate(companyId, new BigDecimal("999.00"), List.of(productId), siteId).gated())
                .isFalse();
    }

    @Test
    void amountOnly_inclusiveThreshold() {
        assertThat(evaluate(new BigDecimal("100.00"), null, null, new BigDecimal("99.99"), siteId).gated()).isFalse();
        assertThat(evaluate(new BigDecimal("100.00"), null, null, new BigDecimal("100.00"), siteId).gated()).isTrue();
        var d = evaluate(new BigDecimal("100.00"), null, null, new BigDecimal("100.00"), siteId);
        assertThat(d.matchedRules()).containsExactly(ApprovalMatchRule.AMOUNT);
    }

    @Test
    void categoryOnly_matchesLineCategory() {
        assertThat(evaluate(null, new UUID[]{categoryId}, null, new BigDecimal("1.00"), siteId).gated()).isTrue();
        assertThat(evaluate(null, new UUID[]{UUID.randomUUID()}, null, new BigDecimal("1.00"), siteId).gated()).isFalse();
    }

    @Test
    void siteOnly_nullNeverMatches() {
        assertThat(evaluate(null, null, new UUID[]{siteId}, new BigDecimal("1.00"), siteId).gated()).isTrue();
        assertThat(evaluate(null, null, new UUID[]{siteId}, new BigDecimal("1.00"), null).gated()).isFalse();
        assertThat(evaluate(null, null, new UUID[]{siteId}, new BigDecimal("1.00"), UUID.randomUUID()).gated()).isFalse();
    }

    @Test
    void orCombinations() {
        var amountCategory = evaluate(new BigDecimal("100.00"), new UUID[]{categoryId}, null,
                new BigDecimal("50.00"), siteId); // amount fails, category matches
        assertThat(amountCategory.gated()).isTrue();
        assertThat(amountCategory.matchedRules()).containsExactly(ApprovalMatchRule.CATEGORY);

        var categorySite = evaluate(null, new UUID[]{categoryId}, new UUID[]{siteId},
                new BigDecimal("50.00"), siteId);
        assertThat(categorySite.gated()).isTrue();
        assertThat(categorySite.matchedRules())
                .containsExactlyInAnyOrder(ApprovalMatchRule.CATEGORY, ApprovalMatchRule.SITE);

        var allThree = evaluate(new BigDecimal("100.00"), new UUID[]{categoryId}, new UUID[]{siteId},
                new BigDecimal("150.00"), siteId);
        assertThat(allThree.gated()).isTrue();
        assertThat(allThree.matchedRules()).containsExactlyInAnyOrder(
                ApprovalMatchRule.AMOUNT, ApprovalMatchRule.CATEGORY, ApprovalMatchRule.SITE);
    }

    @Test
    void noConditionMatches_notGated() {
        assertThat(evaluate(new BigDecimal("1000.00"), new UUID[]{UUID.randomUUID()},
                new UUID[]{UUID.randomUUID()}, new BigDecimal("50.00"), UUID.randomUUID()).gated()).isFalse();
    }

    @Test
    void openApproval_releasesSlotBackToCapacity() {
        UUID placerId = seedUser(jdbc);
        seedMember(jdbc, companyId, placerId, "PROCUREMENT_MANAGER", null);
        LocalDate date = LocalDate.now();
        UUID slotId = ApprovalTestFixtures.seedCounter(jdbc, date, 1, 1); // full at 1/1
        UUID lockId = ApprovalTestFixtures.seedActiveLock(jdbc, placerId, slotId, date);

        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), placerId,
                com.builddash.backend.support.ApprovalTestFixtures.seedAddress(jdbc, placerId), slotId, date, new BigDecimal("150.00"),
                OrderStatus.PENDING_APPROVAL, null, Instant.now(), null, null, List.of(),
                companyId, siteId, null)));

        seedPolicy(jdbc, companyId, new BigDecimal("100.00"), null, null,
                new String[]{"PROCUREMENT_MANAGER"}, 24, 1);
        var decision = gateService.evaluate(companyId, new BigDecimal("150.00"), List.of(productId), siteId);
        assertThat(decision.gated()).isTrue();

        ApprovalRequest request = transactionTemplate.execute(
                status -> gateService.openApproval(order, decision, lockId));

        assertThat(request.status()).isEqualTo(com.builddash.backend.domain.enums.ApprovalRequestStatus.PENDING);
        assertThat(request.escalationDueAt()).isAfter(Instant.now());
        assertThat(request.policyVersion()).isEqualTo(1);
        // Capacity restored while approval pends; the lock row is no longer ACTIVE.
        assertThat(ApprovalTestFixtures.counterCount(jdbc, slotId, date)).isZero();
        String lockStatus = jdbc.queryForObject(
                "SELECT status FROM delivery_slot_locks WHERE id = ?", String.class, lockId);
        assertThat(lockStatus).isEqualTo("RELEASED");
        // Gated order carries no lock id — invisible to the stale sweep by construction.
        Order reloaded = transactionTemplate.execute(s -> orderRepository.findById(order.id()).orElseThrow());
        assertThat(reloaded.deliverySlotLockId()).isNull();
        assertThat(reloaded.status()).isEqualTo(OrderStatus.PENDING_APPROVAL);
    }

    @Test
    void snapshot_survivesPolicyReplacementAndVersionIncrement() {
        UUID placerId = seedUser(jdbc);
        LocalDate date = LocalDate.now();
        UUID slotId = ApprovalTestFixtures.seedCounter(jdbc, date, 5, 0);
        UUID lockId = ApprovalTestFixtures.seedActiveLock(jdbc, placerId, slotId, date);
        seedMember(jdbc, companyId, placerId, "PROCUREMENT_MANAGER", null);

        seedPolicy(jdbc, companyId, new BigDecimal("100.00"), null, null,
                new String[]{"PROCUREMENT_MANAGER", "SITE_SUPERVISOR"}, 24, 1);
        var decision = gateService.evaluate(companyId, new BigDecimal("150.00"), List.of(productId), siteId);
        Order order = transactionTemplate.execute(status -> orderRepository.save(new Order(
                UUID.randomUUID(), placerId,
                com.builddash.backend.support.ApprovalTestFixtures.seedAddress(jdbc, placerId), slotId, date, new BigDecimal("150.00"),
                OrderStatus.PENDING_APPROVAL, null, Instant.now(), null, null, List.of(),
                companyId, siteId, null)));
        ApprovalRequest request = transactionTemplate.execute(
                status -> gateService.openApproval(order, decision, lockId));

        // PUT-equivalent replacement: new threshold, stages, hours, version 2.
        ApprovalPolicy current = policyRepository.findByCompanyId(companyId).orElseThrow();
        policyRepository.save(current.replaced(new BigDecimal("999.00"), null, null,
                List.of(CompanyRole.OWNER), 8, Instant.now()));

        ApprovalRequest reloaded = requestRepository.findById(request.id()).orElseThrow();
        assertThat(reloaded.thresholdAmount()).isEqualByComparingTo("100.00");   // old threshold
        assertThat(reloaded.roleStages()).containsExactly(CompanyRole.PROCUREMENT_MANAGER, CompanyRole.SITE_SUPERVISOR);
        assertThat(reloaded.escalationHours()).isEqualTo(24);
        assertThat(reloaded.policyVersion()).isEqualTo(1);
        assertThat(reloaded.orderTotalAmount()).isEqualByComparingTo("150.00"); // never recomputed

        ApprovalPolicy replaced = policyRepository.findByCompanyId(companyId).orElseThrow();
        assertThat(replaced.version()).isEqualTo(2);
        assertThat(replaced.amountThreshold()).isEqualByComparingTo("999.00");
    }
}
