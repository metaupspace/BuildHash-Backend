package com.builddash.backend.infra.seed;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyContractPrice;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.model.CompanySite;
import com.builddash.backend.domain.port.CompanyContractPriceRepository;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import com.builddash.backend.domain.port.CompanySiteRepository;
import com.builddash.backend.domain.port.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev-only B2B seed (exact CatalogSeeder mechanism: @Profile("dev") ApplicationRunner —
 * the test profile never loads it, and production never sees these rows). Gives
 * Swagger-smokeable company data: one company with OWNER/ADMIN/APPROVER/BUYER members,
 * two sites, and one company contract price demonstrating the pricing tier (OQ-3:
 * seed is the v1 data-entry path for company pricing — no admin API).
 *
 * Idempotent: a second boot finds the seeded name and skips.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class CompanySeeder implements ApplicationRunner {

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final CompanySiteRepository companySiteRepository;
    private final CompanySiteAssignmentRepository companySiteAssignmentRepository;
    private final CompanyContractPriceRepository companyContractPriceRepository;
    private final ProductRepository productRepository;

    /** Fixed id = idempotency: a second dev boot finds it and skips. */
    private static final UUID SEEDED_COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-00000000b2b1");

    @Override
    public void run(ApplicationArguments args) {
        if (companyRepository.findById(SEEDED_COMPANY_ID).isPresent()) {
            return;
        }

        Company company = companyRepository.save(new Company(SEEDED_COMPANY_ID,
                "BuildDash Constructions", "27AAAPZ1234C1ZV", "accounts@builddash.example",
                "Asia/Kolkata", com.builddash.backend.domain.enums.CompanyStatus.ACTIVE, null, null));

        CompanyMember owner = companyMemberRepository.save(new CompanyMember(
                UUID.randomUUID(), company.id(), devUserId(1), CompanyRole.OWNER, null, null));
        CompanyMember admin = companyMemberRepository.save(new CompanyMember(
                UUID.randomUUID(), company.id(), devUserId(2), CompanyRole.ADMIN, null, null));
        CompanyMember approver = companyMemberRepository.save(new CompanyMember(
                UUID.randomUUID(), company.id(), devUserId(3), CompanyRole.APPROVER, null, null));
        companyMemberRepository.save(new CompanyMember(
                UUID.randomUUID(), company.id(), devUserId(4), CompanyRole.BUYER, null, null));

        CompanySite hq = companySiteRepository.save(new CompanySite(
                UUID.randomUUID(), company.id(), "HQ Site", null, true, null, null));
        CompanySite site2 = companySiteRepository.save(new CompanySite(
                UUID.randomUUID(), company.id(), "Andheri Site", null, true, null, null));

        // Approver is scoped to HQ only; everyone else stays unscoped (all sites)
        companySiteAssignmentRepository.replaceForMember(approver.id(), List.of(hq.id()));
        companySiteAssignmentRepository.replaceForMember(admin.id(), List.of(site2.id()));

        // Company contract price for the first seeded product, if the catalog seed has
        // already run (runner order vs CatalogSeeder is not guaranteed — skip quietly
        // when no product exists yet; pricing precedence is exercised by tests anyway).
        Optional<UUID> firstProduct = productRepository.findPage(null, null, null, 1).stream()
                .findFirst()
                .map(com.builddash.backend.domain.model.Product::getId);
        if (firstProduct.isPresent()) {
            companyContractPriceRepository.save(new CompanyContractPrice(
                    UUID.randomUUID(), company.id(), firstProduct.get(),
                    new BigDecimal("299.00"), Instant.now(), null, null, null));
            log.info("Seeded B2B company 'BuildDash Constructions' ({} members incl. owner {}, 2 sites, 1 company contract price)",
                    4, owner.id());
        } else {
            log.info("Seeded B2B company 'BuildDash Constructions' (no products yet — company contract price skipped)");
        }
    }

    /** Deterministic dev-only user ids so repeated boots map members to the same fake users. */
    private static UUID devUserId(int n) {
        return UUID.fromString("00000000-0000-0000-0000-0000000000" + String.format("%02d", n));
    }
}
