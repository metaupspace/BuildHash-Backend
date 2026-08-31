package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.exception.ContractPriceOverlapException;
import com.builddash.backend.domain.model.CompanyContractPrice;
import com.builddash.backend.domain.port.CompanyContractPriceRepository;
import com.builddash.backend.infra.persistence.entity.CompanyContractPriceEntity;
import com.builddash.backend.infra.persistence.repository.CompanyContractPriceJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Same double enforcement as ContractPriceRepositoryAdapter: an application-level
 * overlap check for a clear error on the common path, plus saveAndFlush so the V25
 * GiST exclusion constraint (the concurrency backstop) fires inside this method and
 * surfaces as the same domain exception on the racing path.
 */
@Repository
@RequiredArgsConstructor
class CompanyContractPriceRepositoryAdapter implements CompanyContractPriceRepository {

    private final CompanyContractPriceJpaRepository jpaRepository;

    @Override
    public Optional<CompanyContractPrice> findActive(UUID companyId, UUID productId, Instant asOf) {
        return jpaRepository.findByCompanyIdAndProductId(companyId, productId).stream()
                .filter(entity -> !entity.getEffectiveFrom().isAfter(asOf))
                .filter(entity -> entity.getEffectiveTo() == null || entity.getEffectiveTo().isAfter(asOf))
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public CompanyContractPrice save(CompanyContractPrice price) {
        List<CompanyContractPriceEntity> existing = jpaRepository.findByCompanyIdAndProductId(
                price.companyId(), price.productId());

        boolean overlaps = existing.stream()
                .filter(entity -> !entity.getId().equals(price.id()))
                .anyMatch(entity -> windowsOverlap(entity, price));
        if (overlaps) {
            throw overlap(price);
        }

        try {
            return toDomain(jpaRepository.saveAndFlush(toEntity(price)));
        } catch (DataIntegrityViolationException ex) {
            throw overlap(price);
        }
    }

    private static ContractPriceOverlapException overlap(CompanyContractPrice price) {
        return new ContractPriceOverlapException(
                "Company contract price window overlaps an existing row for company " + price.companyId()
                        + " and product " + price.productId());
    }

    private static boolean windowsOverlap(CompanyContractPriceEntity existing, CompanyContractPrice candidate) {
        boolean existingStartsBeforeCandidateEnds = candidate.effectiveTo() == null
                || existing.getEffectiveFrom().isBefore(candidate.effectiveTo());
        boolean candidateStartsBeforeExistingEnds = existing.getEffectiveTo() == null
                || candidate.effectiveFrom().isBefore(existing.getEffectiveTo());
        return existingStartsBeforeCandidateEnds && candidateStartsBeforeExistingEnds;
    }

    private CompanyContractPriceEntity toEntity(CompanyContractPrice price) {
        CompanyContractPriceEntity entity = price.id() == null
                ? new CompanyContractPriceEntity()
                : jpaRepository.findById(price.id()).orElseGet(CompanyContractPriceEntity::new);
        entity.setCompanyId(price.companyId());
        entity.setProductId(price.productId());
        entity.setUnitPrice(price.unitPrice());
        entity.setEffectiveFrom(price.effectiveFrom());
        entity.setEffectiveTo(price.effectiveTo());
        return entity;
    }

    private CompanyContractPrice toDomain(CompanyContractPriceEntity entity) {
        return new CompanyContractPrice(entity.getId(), entity.getCompanyId(), entity.getProductId(),
                entity.getUnitPrice(), entity.getEffectiveFrom(), entity.getEffectiveTo(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
