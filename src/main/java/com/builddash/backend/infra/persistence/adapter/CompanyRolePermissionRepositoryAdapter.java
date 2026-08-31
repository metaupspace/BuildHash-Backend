package com.builddash.backend.infra.persistence.adapter;

import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.port.CompanyRolePermissionRepository;
import com.builddash.backend.infra.persistence.entity.CompanyRolePermissionEntity;
import com.builddash.backend.infra.persistence.repository.CompanyRolePermissionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MANDATORY propagation: replaceRolePermissions joins the caller's transaction —
 * the company-row lock held by the mutating service is the serialization point for
 * concurrent permission edits (no partial sets, last valid replacement wins).
 */
@Repository
@RequiredArgsConstructor
class CompanyRolePermissionRepositoryAdapter implements CompanyRolePermissionRepository {

    private final CompanyRolePermissionJpaRepository jpaRepository;

    @Override
    public Set<CompanyPermission> findPermissions(UUID companyId, CompanyRole role) {
        return jpaRepository.findByCompanyIdAndRole(companyId, role.name()).stream()
                .map(e -> CompanyPermission.valueOf(e.getPermission()))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceRolePermissions(UUID companyId, CompanyRole role, Set<CompanyPermission> permissions) {
        jpaRepository.deleteByCompanyIdAndRole(companyId, role.name());
        jpaRepository.flush();
        java.time.Instant now = java.time.Instant.now();
        for (CompanyPermission permission : permissions) {
            CompanyRolePermissionEntity entity = new CompanyRolePermissionEntity(
                    companyId, role.name(), permission.name(), now);
            jpaRepository.save(entity);
        }
    }

    @Override
    public Set<CompanyRole> findRolesWithPermission(UUID companyId, CompanyPermission permission) {
        return jpaRepository.findByCompanyIdAndPermission(companyId, permission.name()).stream()
                .map(e -> CompanyRole.valueOf(e.getRole()))
                .collect(Collectors.toCollection(HashSet::new));
    }
}
