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
import java.util.UUID;

/**
 * Join row member -> site. IdClass over EmbeddedId because the codebase has no
 * embeddable precedent and IdClass keeps the entity free of a nested id type.
 * PK (member_id, site_id) mirrors the table's composite primary key (V25).
 */
@Entity
@Table(name = "company_site_assignments")
@IdClass(CompanySiteAssignmentEntity.AssignmentId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanySiteAssignmentEntity {

    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @Id
    @Column(name = "site_id")
    private UUID siteId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class AssignmentId implements Serializable {
        private UUID memberId;
        private UUID siteId;
    }
}
