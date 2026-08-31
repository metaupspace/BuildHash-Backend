package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.RfqQuoteStatus;
import com.builddash.backend.domain.exception.DuplicateQuoteException;
import com.builddash.backend.domain.model.RfqQuote;
import com.builddash.backend.domain.port.RfqQuoteRepository;
import com.builddash.backend.infra.persistence.entity.RfqQuoteEntity;
import com.builddash.backend.infra.persistence.repository.RfqQuoteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The UNIQUE (rfq_id, vendor_id) constraint is the final duplicate protection:
 * a losing racer that slips past the application check surfaces here as
 * DuplicateQuoteException (409), not a raw DataIntegrityViolationException.
 */
@Repository
@RequiredArgsConstructor
class RfqQuoteRepositoryAdapter implements RfqQuoteRepository {

    private final RfqQuoteJpaRepository jpaRepository;

    @Override
    public RfqQuote save(RfqQuote quote) {
        RfqQuoteEntity entity = new RfqQuoteEntity();
        entity.setId(quote.id());
        entity.setRfqId(quote.rfqId());
        entity.setVendorId(quote.vendorId());
        entity.setTotalAmount(quote.totalAmount());
        entity.setValidUntil(quote.validUntil());
        entity.setStatus(RfqQuoteStatus.SUBMITTED);
        entity.setSubmittedAt(quote.submittedAt());
        RfqQuoteEntity saved;
        try {
            saved = jpaRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateQuoteException();
        }
        // Return the persisted instance's state, never the caller's input: with an
        // id generator on the entity the stored id is the source of truth.
        return toDomain(saved);
    }

    @Override
    public Optional<RfqQuote> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<RfqQuote> findByRfqIdAndVendorId(UUID rfqId, UUID vendorId) {
        return jpaRepository.findByRfqIdAndVendorId(rfqId, vendorId).map(this::toDomain);
    }

    @Override
    public List<RfqQuote> findByRfqIdOrderByTotalAmountAsc(UUID rfqId) {
        return jpaRepository.findByRfqIdOrderByTotalAmountAsc(rfqId).stream().map(this::toDomain).toList();
    }

    private RfqQuote toDomain(RfqQuoteEntity e) {
        return new RfqQuote(e.getId(), e.getRfqId(), e.getVendorId(), e.getTotalAmount(),
                e.getValidUntil(), e.getStatus(), e.getSubmittedAt());
    }
}
