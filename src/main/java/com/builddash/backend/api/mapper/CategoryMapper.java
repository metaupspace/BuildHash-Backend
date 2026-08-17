package com.builddash.backend.api.mapper;

import com.builddash.backend.api.dto.response.CategoryResponse;
import com.builddash.backend.domain.model.Category;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId().toString(),
                category.getName(),
                category.getSlug(),
                category.getParentId() == null ? null : category.getParentId().toString(),
                category.getAttributeSchema()
        );
    }

    public List<CategoryResponse> toResponseList(List<Category> categories) {
        return categories.stream().map(this::toResponse).toList();
    }
}
