package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.PoRowStatus;
import com.builddash.backend.domain.model.PoImportRow;
import com.builddash.backend.domain.port.PoImportRowRepository;
import com.builddash.backend.infra.persistence.entity.PoImportRowEntity;
import com.builddash.backend.infra.persistence.repository.PoImportRowJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class PoImportRowRepositoryAdapter implements PoImportRowRepository {

    private final PoImportRowJpaRepository jpaRepository;

    @Override
    @Transactional
    public List<PoImportRow> saveAll(List<PoImportRow> rows) {
        List<PoImportRow> persisted = new ArrayList<>(rows.size());
        for (PoImportRow row : rows) {
            PoImportRowEntity entity = new PoImportRowEntity();
            entity.setId(row.id());
            entity.setImportId(row.importId());
            entity.setRowIndex(row.rowIndex());
            entity.setProductSlug(row.productSlug());
            entity.setQuantity(row.quantity());
            entity.setStatus(row.status());
            entity.setErrorCode(row.errorCode());
            PoImportRowEntity saved = jpaRepository.saveAndFlush(entity);
            persisted.add(new PoImportRow(saved.getId(), saved.getImportId(), saved.getRowIndex(),
                    saved.getProductSlug(), saved.getQuantity(), saved.getStatus(), saved.getErrorCode()));
        }
        return persisted;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PoImportRow> findByImportId(UUID importId) {
        return jpaRepository.findByImportId(importId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countValidByImportId(UUID importId) {
        return jpaRepository.countByImportIdAndStatus(importId, PoRowStatus.VALID);
    }

    private PoImportRow toDomain(PoImportRowEntity e) {
        return new PoImportRow(e.getId(), e.getImportId(), e.getRowIndex(), e.getProductSlug(),
                e.getQuantity(), e.getStatus(), e.getErrorCode());
    }
}
