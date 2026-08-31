package com.builddash.backend.infra.seed;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyContractPrice;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.model.CompanySite;
import com.builddash.backend.domain.port.CompanyContractPriceRepository;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import com.builddash.backend.domain.port.CompanySiteRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.service.CompanyPermissionDefaults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Dev-only B2B seed (exact CatalogSeeder mechanism: @Profile("dev") ApplicationRunner —
 * the test profile never loads it, production never sees these rows). Gives
 * Swagger-smokeable company data: one company with one member per role, two sites,
 * and the default permission profiles (OWNER gets none — implicit ALL).
 *
 * Idempotent: a second boot finds the seeded id and skips.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class CompanySeeder implements ApplicationRunner {

    /** Fixed id = idempotency: a second dev boot finds it and skips. */
    private static final UUID SEEDED_COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-00000000b2b1");

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final CompanySiteRepository companySiteRepository;
    private final CompanySiteAssignmentRepository companySiteAssignmentRepository;
    private final CompanyRolePermissionRepository companyRolePermissionRepository;
    private final CompanyContractPriceRepository companyContractPriceRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (companyRepository.findById(SEEDED_COMPANY_ID).isPresent()) {
            return;
        }

        Company company = companyRepository.save(new Company(SEEDED_COMPANY_ID,
                "BuildDash Constructions", "27AAAPZ1234C1ZV", "accounts@builddash.example",
                "Asia/Kolkata", com.builddash.backend.domain.enums.CompanyStatus.ACTIVE, null, null));

        CompanyMember owner = companyMemberRepository.save(new CompanyMember(
                devMemberId(1), company.id(), devUserId(1), CompanyRole.OWNER, null, null));
        CompanyMember procurementManager = companyMemberRepository.save(new CompanyMember(
                devMemberId(2), company.id(), devUserId(2), CompanyRole.PROCUREMENT_MANAGER, null, null));
        CompanyMember siteSupervisor = companyMemberRepository.save(new CompanyMember(
                devMemberId(3), company.id(), devUserId(3), CompanyRole.SITE_SUPERVISOR, null, null));
        companyMemberRepository.save(new CompanyMember(
                devMemberId(4), company.id(), devUserId(4), CompanyRole.ACCOUNTANT, null, null));
        companyMemberRepository.save(new CompanyMember(
                devMemberId(5), company.id(), devUserId(5), CompanyRole.VIEWER, null, null));

        CompanySite hq = companySiteRepository.save(new CompanySite(
                UUID.randomUUID(), company.id(), "HQ Site", null, true, null, null));
        CompanySite site2 = companySiteRepository.save(new CompanySite(
                UUID.randomUUID(), company.id(), "Andheri Site", null, true, null, null));

        // SITE_SUPERVISOR scoped to HQ; PROCUREMENT_MANAGER scoped to the second site;
        // OWNER/ACCOUNTANT/VIEWER stay unscoped (all sites)
        companySiteAssignmentRepository.replaceForMember(siteSupervisor.id(), List.of(hq.id()));
        companySiteAssignmentRepository.replaceForMember(procurementManager.id(), List.of(site2.id()));

        // Default permission profiles for the four customizable roles — same rows the
        // CompanyServiceImpl.create flow seeds. OWNER rows are never written (implicit).
        for (CompanyRole role : CompanyPermissionDefaults.customizableRoles()) {
            companyRolePermissionRepository.replaceRolePermissions(
                    company.id(), role, CompanyPermissionDefaults.forRole(role));
        }

        // Company contract price for the first seeded product, if the catalog seed has
        // already run (runner order vs CatalogSeeder is not guaranteed — skip quietly).
        Optional<UUID> firstProduct = productRepository.findPage(null, null, null, 1).stream()
                .findFirst()
                .map(com.builddash.backend.domain.model.Product::getId);
        if (firstProduct.isPresent()) {
            companyContractPriceRepository.save(new CompanyContractPrice(
                    UUID.randomUUID(), company.id(), firstProduct.get(),
                    new BigDecimal("299.00"), Instant.now(), null, null, null));
            log.info("Seeded B2B company 'BuildDash Constructions' (5 members incl. owner {}, 2 sites, default permission profiles, 1 company contract price)",
                    owner.id());
        } else {
            log.info("Seeded B2B company 'BuildDash Constructions' (no products yet — company contract price skipped)");
        }
    }

    /** Deterministic dev-only ids so repeated boots map members to the same fake users. */
    private static UUID devUserId(int n) {
        return UUID.fromString("00000000-0000-0000-0000-0000000000" + String.format("%02d", n));
    }

    private static UUID devMemberId(int n) {
        return UUID.fromString("00000000-0000-0000-0001-0000000000" + String.format("%02d", n));
    }
}
