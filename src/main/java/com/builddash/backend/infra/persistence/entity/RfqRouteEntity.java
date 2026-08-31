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

/** Creation-time routing snapshot; composite PK (rfq_id, vendor_id). */
@Entity
@Table(name = "rfq_routes")
@IdClass(RfqRouteEntity.RfqRouteId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RfqRouteEntity {

    @Id
    @Column(name = "rfq_id")
    private UUID rfqId;

    @Id
    @Column(name = "vendor_id")
    private UUID vendorId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class RfqRouteId implements Serializable {
        private UUID rfqId;
        private UUID vendorId;
    }
}
