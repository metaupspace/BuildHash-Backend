package com.builddash.backend.infra.persistence.entity;

import com.builddash.backend.domain.enums.PoRowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "po_import_rows")
@Getter
@Setter
@NoArgsConstructor
public class PoImportRowEntity {

    @Id
    private UUID id;

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "product_slug")
    private String productSlug;

    @Column(name = "quantity")
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PoRowStatus status;

    @Column(name = "error_code")
    private String errorCode;
}
