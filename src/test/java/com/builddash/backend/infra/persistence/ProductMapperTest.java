package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductImage;
import com.builddash.backend.domain.model.StockEntry;
import com.builddash.backend.infra.persistence.entity.ProductEntity;
import com.builddash.backend.infra.persistence.mapper.ProductMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    @Test
    void toEntity_thenToDomain_roundTripsEveryField() {
        Product original = new Product();
        original.setId(UUID.randomUUID());
        original.setName("UltraTech Cement OPC 53 Grade 50kg");
        original.setSlug("ultratech-cement-opc-53-grade-50kg");
        original.setCategoryId(UUID.randomUUID());
        original.setBrand("UltraTech");
        original.setHsnCode("2523");
        original.setAttributes(Map.of("weightKg", 50, "gradeType", "OPC53"));
        original.setImages(List.of(new ProductImage("https://example.com/img.jpg", "Cement bag", 0)));
        original.setStock(List.of(new StockEntry("WH-1", 100)));
        original.setStatus(ProductStatus.ACTIVE);
        original.setCreatedAt(Instant.now());
        original.setUpdatedAt(Instant.now());

        ProductEntity entity = ProductMapper.toEntity(original);
        Product roundTripped = ProductMapper.toDomain(entity);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getName()).isEqualTo(original.getName());
        assertThat(roundTripped.getSlug()).isEqualTo(original.getSlug());
        assertThat(roundTripped.getCategoryId()).isEqualTo(original.getCategoryId());
        assertThat(roundTripped.getBrand()).isEqualTo(original.getBrand());
        assertThat(roundTripped.getHsnCode()).isEqualTo(original.getHsnCode());
        assertThat(roundTripped.getAttributes()).isEqualTo(original.getAttributes());
        assertThat(roundTripped.getImages()).isEqualTo(original.getImages());
        assertThat(roundTripped.getStock()).isEqualTo(original.getStock());
        assertThat(roundTripped.getStatus()).isEqualTo(original.getStatus());
        assertThat(roundTripped.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(roundTripped.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }
}
