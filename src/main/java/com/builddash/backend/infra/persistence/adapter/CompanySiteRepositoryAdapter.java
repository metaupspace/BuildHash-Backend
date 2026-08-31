package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.model.CompanySite;
import com.builddash.backend.domain.port.CompanySiteRepository;
import com.builddash.backend.infra.persistence.entity.CompanySiteEntity;
import com.builddash.backend.infra.persistence.repository.CompanySiteJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class CompanySiteRepositoryAdapter implements CompanySiteRepository {

    private final CompanySiteJpaRepository jpaRepository;

    @Override
    public CompanySite save(CompanySite site) {
        CompanySiteEntity entity = jpaRepository.findById(site.id())
                .orElseGet(() -> {
                    CompanySiteEntity e = new CompanySiteEntity();
                    e.setId(site.id());
                    return e;
                });
        entity.setCompanyId(site.companyId());
        entity.setName(site.name());
        entity.setAddressId(site.addressId());
        entity.setActive(site.active());
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<CompanySite> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public CompanySite findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(this::toDomain)
                .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException(
                        "COMPANY_SITE_NOT_FOUND", "Company site not found: " + id));
    }

    @Override
    public List<CompanySite> findByCompanyId(UUID companyId) {
        return jpaRepository.findByCompanyId(companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private CompanySite toDomain(CompanySiteEntity entity) {
        return new CompanySite(entity.getId(), entity.getCompanyId(), entity.getName(),
                entity.getAddressId(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
