package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.infra.persistence.entity.CompanyMemberEntity;
import com.builddash.backend.infra.persistence.repository.CompanyMemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class CompanyMemberRepositoryAdapter implements CompanyMemberRepository {

    private final CompanyMemberJpaRepository jpaRepository;

    @Override
    public CompanyMember save(CompanyMember member) {
        CompanyMemberEntity entity = jpaRepository.findById(member.id())
                .orElseGet(() -> {
                    CompanyMemberEntity e = new CompanyMemberEntity();
                    e.setId(member.id());
                    return e;
                });
        entity.setCompanyId(member.companyId());
        entity.setUserId(member.userId());
        entity.setRole(member.role());
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<CompanyMember> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<CompanyMember> findByCompanyIdAndUserId(UUID companyId, UUID userId) {
        return jpaRepository.findByCompanyIdAndUserId(companyId, userId).map(this::toDomain);
    }

    @Override
    public List<CompanyMember> findByCompanyId(UUID companyId) {
        return jpaRepository.findByCompanyId(companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CompanyMember> findByCompanyIdForUpdate(UUID companyId) {
        return jpaRepository.findByCompanyIdForUpdate(companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CompanyMember> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private CompanyMember toDomain(CompanyMemberEntity entity) {
        return new CompanyMember(entity.getId(), entity.getCompanyId(), entity.getUserId(),
                entity.getRole() != null ? entity.getRole() : CompanyRole.BUYER,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
