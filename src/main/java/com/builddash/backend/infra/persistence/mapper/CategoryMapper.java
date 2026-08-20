package com.builddash.backend.infra.persistence.mapper;

import com.builddash.backend.domain.model.Category;
import com.builddash.backend.infra.persistence.entity.CategoryEntity;

public final class CategoryMapper {

    private CategoryMapper() {
    }

    public static Category toDomain(CategoryEntity entity) {
        Category category = new Category();
        category.setId(entity.getId());
        category.setName(entity.getName());
        category.setSlug(entity.getSlug());
        category.setParentId(entity.getParentId());
        category.setAttributeSchema(entity.getAttributeSchema());
        return category;
    }

    public static CategoryEntity toEntity(Category category) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(category.getId());
        entity.setName(category.getName());
        entity.setSlug(category.getSlug());
        entity.setParentId(category.getParentId());
        entity.setAttributeSchema(category.getAttributeSchema());
        return entity;
    }
}
