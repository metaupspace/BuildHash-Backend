package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.PoImport;
import com.builddash.backend.domain.port.PoImportRepository;
import com.builddash.backend.infra.persistence.entity.PoImportEntity;
import com.builddash.backend.infra.persistence.repository.PoImportJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class PoImportRepositoryAdapter implements PoImportRepository {

    private final PoImportJpaRepository jpaRepository;

    @Override
    @Transactional
    public PoImport save(PoImport poImport) {
        PoImportEntity entity = jpaRepository.findById(poImport.id())
                .orElseGet(() -> {
                    PoImportEntity e = new PoImportEntity();
                    e.setId(poImport.id());
                    return e;
                });
        entity.setCompanyId(poImport.companyId());
        entity.setIdempotencyKey(poImport.idempotencyKey());
        entity.setUploadedBy(poImport.uploadedBy());
        entity.setStatus(poImport.status());
        entity.setTotalRows(poImport.totalRows());
        entity.setValidRows(poImport.validRows());
        entity.setInvalidRows(poImport.invalidRows());
        entity.setDraftCartId(poImport.draftCartId());
        PoImportEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PoImport> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional
    public Optional<PoImport> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PoImport> findByCompanyIdAndIdempotencyKey(UUID companyId, String idempotencyKey) {
        return jpaRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey).map(this::toDomain);
    }

    private PoImport toDomain(PoImportEntity e) {
        return new PoImport(e.getId(), e.getCompanyId(), e.getIdempotencyKey(), e.getUploadedBy(),
                e.getStatus(), e.getTotalRows(), e.getValidRows(), e.getInvalidRows(),
                e.getDraftCartId(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
