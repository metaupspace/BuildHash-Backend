package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.CategoryAttribute;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class CategoryEntity {

    @Id
    @UuidGenerator
    private UUID id;

    private String name;
    private String slug;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "attribute_schema")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<CategoryAttribute> attributeSchema = new ArrayList<>();
}
