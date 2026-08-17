package com.builddash.backend.domain.service;

import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.enums.AttributeType;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.CategoryAttribute;
import com.builddash.backend.domain.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaDrivenProductFactoryTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private SchemaDrivenProductFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SchemaDrivenProductFactory();
    }

    private Category cementCategory() {
        Category category = new Category();
        category.setId(CATEGORY_ID);
        category.setName("Cement");
        category.setSlug("cement");
        category.setAttributeSchema(List.of(
                new CategoryAttribute("weightKg", "Weight (kg)", AttributeType.NUMBER, true, "kg", null),
                new CategoryAttribute("gradeType", "Grade", AttributeType.ENUM, true, null,
                        List.of("OPC33", "OPC43", "OPC53", "PPC")),
                new CategoryAttribute("waterproof", "Waterproof", AttributeType.BOOLEAN, false, null, null)
        ));
        return category;
    }

    @Test
    void build_validAttributes_returnsProductWithValidatedAttributesAndSlug() {
        Product product = factory.build(cementCategory(), "UltraTech Cement OPC 53 Grade 50kg", "UltraTech", "2523",
                Map.of("weightKg", 50, "gradeType", "OPC53"), List.of());

        assertThat(product.getSlug()).isEqualTo("ultratech-cement-opc-53-grade-50kg");
        assertThat(product.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(product.getHsnCode()).isEqualTo("2523");
        assertThat(product.getAttributes()).containsEntry("weightKg", 50).containsEntry("gradeType", "OPC53");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void build_missingRequiredAttribute_throwsBadRequest() {
        assertThatThrownBy(() -> factory.build(cementCategory(), "Cement Bag", "Brand", "2523",
                Map.of("weightKg", 50), List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("gradeType");
    }

    @Test
    void build_wrongAttributeType_throwsBadRequest() {
        assertThatThrownBy(() -> factory.build(cementCategory(), "Cement Bag", "Brand", "2523",
                Map.of("weightKg", "fifty", "gradeType", "OPC53"), List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("weightKg");
    }

    @Test
    void build_enumValueNotInAllowedList_throwsBadRequest() {
        assertThatThrownBy(() -> factory.build(cementCategory(), "Cement Bag", "Brand", "2523",
                Map.of("weightKg", 50, "gradeType", "NOT_A_GRADE"), List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("gradeType");
    }

    @Test
    void build_optionalAttributeOmitted_succeeds() {
        Product product = factory.build(cementCategory(), "Cement Bag", "Brand", "2523",
                Map.of("weightKg", 50, "gradeType", "OPC53"), List.of());

        assertThat(product.getAttributes()).doesNotContainKey("waterproof");
    }
}
