package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.infra.persistence.entity.CompanyEntity;
import com.builddash.backend.infra.persistence.repository.CompanyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class CompanyRepositoryAdapter implements CompanyRepository {

    private final CompanyJpaRepository jpaRepository;

    @Override
    public Company save(Company company) {
        CompanyEntity entity = jpaRepository.findById(company.id())
                .orElseGet(() -> {
                    CompanyEntity e = new CompanyEntity();
                    e.setId(company.id());
                    return e;
                });
        entity.setName(company.name());
        entity.setGstNumber(company.gstNumber());
        entity.setStatementEmail(company.statementEmail());
        entity.setBusinessTimezone(company.businessTimezone());
        entity.setStatus(company.status());
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Company findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(this::toDomain)
                .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException(
                        "COMPANY_NOT_FOUND", "Company not found: " + id));
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private Company toDomain(CompanyEntity entity) {
        return new Company(entity.getId(), entity.getName(), entity.getGstNumber(),
                entity.getStatementEmail(), entity.getBusinessTimezone(),
                entity.getStatus() != null ? entity.getStatus() : CompanyStatus.ACTIVE,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
