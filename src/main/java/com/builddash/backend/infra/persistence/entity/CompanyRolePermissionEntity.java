package com.builddash.backend.infra.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * One granted permission for one (company, role). String-typed key columns mirror
 * the V26 CHECK constraints exactly — the database rejects invalid values before
 * the enum mapping in the adapter ever runs. Composite PK (company_id, role,
 * permission) gives set semantics and dedup for free.
 */
@Entity
@Table(name = "company_role_permissions")
@IdClass(CompanyRolePermissionEntity.CompanyRolePermissionId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyRolePermissionEntity {

    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @Id
    @Column(name = "role")
    private String role;

    @Id
    @Column(name = "permission")
    private String permission;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class CompanyRolePermissionId implements Serializable {
        private UUID companyId;
        private String role;
        private String permission;
    }
}
