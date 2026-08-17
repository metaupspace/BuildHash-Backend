package com.builddash.backend.api.dto.response;

import com.builddash.backend.domain.model.CategoryAttribute;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "CategoryResponse")
public record CategoryResponse(
        String id,
        @Schema(example = "Cement") String name,
        @Schema(example = "cement") String slug,
        String parentId,
        List<CategoryAttribute> attributeSchema
) {
}
