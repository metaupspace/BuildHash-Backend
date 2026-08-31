package com.builddash.backend.infra.persistence.repository;

import com.builddash.backend.infra.persistence.entity.CompanyRolePermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyRolePermissionJpaRepository
        extends JpaRepository<CompanyRolePermissionEntity, CompanyRolePermissionEntity.CompanyRolePermissionId> {

    List<CompanyRolePermissionEntity> findByCompanyIdAndRole(UUID companyId, String role);

    List<CompanyRolePermissionEntity> findByCompanyIdAndPermission(UUID companyId, String permission);

    void deleteByCompanyIdAndRole(UUID companyId, String role);
}
