package com.builddash.backend.infra.persistence;

import com.builddash.backend.domain.model.Product;

final class ProductMapper {

    private ProductMapper() {
    }

    static Product toDomain(ProductEntity entity) {
        Product product = new Product();
        product.setId(entity.getId());
        product.setName(entity.getName());
        product.setSlug(entity.getSlug());
        product.setCategoryId(entity.getCategoryId());
        product.setBrand(entity.getBrand());
        product.setHsnCode(entity.getHsnCode());
        product.setAttributes(entity.getAttributes());
        product.setImages(entity.getImages());
        product.setStock(entity.getStock());
        product.setStatus(entity.getStatus());
        product.setCreatedAt(entity.getCreatedAt());
        product.setUpdatedAt(entity.getUpdatedAt());
        return product;
    }

    static ProductEntity toEntity(Product product) {
        ProductEntity entity = new ProductEntity();
        entity.setId(product.getId());
        entity.setName(product.getName());
        entity.setSlug(product.getSlug());
        entity.setCategoryId(product.getCategoryId());
        entity.setBrand(product.getBrand());
        entity.setHsnCode(product.getHsnCode());
        entity.setAttributes(product.getAttributes());
        entity.setImages(product.getImages());
        entity.setStock(product.getStock());
        entity.setStatus(product.getStatus());
        entity.setCreatedAt(product.getCreatedAt());
        entity.setUpdatedAt(product.getUpdatedAt());
        return entity;
    }
}
