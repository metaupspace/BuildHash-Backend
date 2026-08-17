package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.AttributeType;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.CategoryAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMapperTest {

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        Category original = new Category();
        original.setId(UUID.randomUUID());
        original.setName("Cement");
        original.setSlug("cement");
        original.setParentId(UUID.randomUUID());
        original.setAttributeSchema(List.of(
                new CategoryAttribute("weightKg", "Weight (kg)", AttributeType.NUMBER, true, "kg", null),
                new CategoryAttribute("gradeType", "Grade", AttributeType.ENUM, true, null,
                        List.of("OPC33", "OPC43", "OPC53", "PPC"))
        ));

        CategoryEntity entity = CategoryMapper.toEntity(original);
        Category roundTripped = CategoryMapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getName()).isEqualTo(original.getName());
        assertThat(roundTripped.getSlug()).isEqualTo(original.getSlug());
        assertThat(roundTripped.getParentId()).isEqualTo(original.getParentId());
        assertThat(roundTripped.getAttributeSchema()).isEqualTo(original.getAttributeSchema());
    }

    @Test
    void toEntity_thenToDomain_handlesNullParentId() {
        Category original = new Category();
        original.setId(UUID.randomUUID());
        original.setName("Top-level Category");
        original.setSlug("top-level");
        original.setParentId(null);

        CategoryEntity entity = CategoryMapper.toEntity(original);
        Category roundTripped = CategoryMapper.toDomain(entity);

        assertThat(roundTripped.getParentId()).isNull();
    }
}
