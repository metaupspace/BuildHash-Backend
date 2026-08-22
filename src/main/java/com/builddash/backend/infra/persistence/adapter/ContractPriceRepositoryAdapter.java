package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.exception.ContractPriceOverlapException;
import com.builddash.backend.domain.model.ContractPrice;
import com.builddash.backend.domain.port.ContractPriceRepository;
import com.builddash.backend.infra.persistence.entity.ContractPriceEntity;
import com.builddash.backend.infra.persistence.mapper.ContractPriceMapper;
import com.builddash.backend.infra.persistence.repository.ContractPriceJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Overlap rejection is enforced twice, deliberately:
 *  - here, an application-level check against the small existing user+product set, so a
 *    normal (non-racing) write gets a clear ContractPriceOverlapException with no DB
 *    round-trip surprises.
 *  - the excl_contract_pricing_no_overlap GiST exclusion constraint (V7 migration), which
 *    is the actual concurrency backstop: two concurrent inserts can both pass the check
 *    above before either commits, but Postgres enforces the exclusion constraint at
 *    statement execution time, so only one insert can ever succeed. saveAndFlush forces
 *    that check to happen inside this method (instead of at end-of-transaction, by which
 *    point this catch block is long gone), so the race also surfaces as the same domain
 *    exception, not a raw DataIntegrityViolationException.
 */
@Repository
@RequiredArgsConstructor
class ContractPriceRepositoryAdapter implements ContractPriceRepository {

    private final ContractPriceJpaRepository jpaRepository;


    @Override
    public Optional<ContractPrice> findActive(UUID userId, UUID productId, Instant asOf) {
        return jpaRepository.findByUserIdAndProductId(userId, productId).stream()
                .filter(entity -> !entity.getEffectiveFrom().isAfter(asOf))
                .filter(entity -> entity.getEffectiveTo() == null || entity.getEffectiveTo().isAfter(asOf))
                .findFirst()
                .map(ContractPriceMapper::toDomain);
    }

    @Override
    public ContractPrice save(ContractPrice contractPrice) {
        List<ContractPriceEntity> existing = jpaRepository.findByUserIdAndProductId(
                contractPrice.getUserId(), contractPrice.getProductId());

        boolean overlaps = existing.stream()
                .filter(entity -> !entity.getId().equals(contractPrice.getId()))
                .anyMatch(entity -> windowsOverlap(entity, contractPrice));
        if (overlaps) {
            throw new ContractPriceOverlapException(
                    "Contract price window overlaps an existing row for user " + contractPrice.getUserId()
                            + " and product " + contractPrice.getProductId());
        }

        try {
            return ContractPriceMapper.toDomain(
                    jpaRepository.saveAndFlush(ContractPriceMapper.toEntity(contractPrice)));
        } catch (DataIntegrityViolationException ex) {
            throw new ContractPriceOverlapException(
                    "Contract price window overlaps an existing row for user " + contractPrice.getUserId()
                            + " and product " + contractPrice.getProductId());
        }
    }

    private static boolean windowsOverlap(ContractPriceEntity existing, ContractPrice candidate) {
        boolean existingStartsBeforeCandidateEnds = candidate.getEffectiveTo() == null
                || existing.getEffectiveFrom().isBefore(candidate.getEffectiveTo());
        boolean candidateStartsBeforeExistingEnds = existing.getEffectiveTo() == null
                || candidate.getEffectiveFrom().isBefore(existing.getEffectiveTo());
        return existingStartsBeforeCandidateEnds && candidateStartsBeforeExistingEnds;
    }
}
