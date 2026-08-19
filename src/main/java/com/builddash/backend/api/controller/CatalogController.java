package com.builddash.backend.api.controller;

import com.builddash.backend.api.dto.response.CategoryResponse;
import com.builddash.backend.api.dto.response.ProductDetailResponse;
import com.builddash.backend.api.dto.response.ProductListResponse;
import com.builddash.backend.api.mapper.CategoryMapper;
import com.builddash.backend.api.mapper.ProductMapper;
import com.builddash.backend.application.service.CategoryReader;
import com.builddash.backend.application.service.ProductReader;
import com.builddash.backend.api.dto.ApiError;
import com.builddash.backend.domain.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Catalog", description = "Category and product browse endpoints (public, no auth required)")
public class CatalogController {

    private final CategoryReader categoryReader;
    private final ProductReader productReader;
    private final CategoryMapper categoryMapper;
    private final ProductMapper productMapper;

    public CatalogController(CategoryReader categoryReader, ProductReader productReader,
                              CategoryMapper categoryMapper, ProductMapper productMapper) {
        this.categoryReader = categoryReader;
        this.productReader = productReader;
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
    }

    @GetMapping("/categories")
    @Operation(summary = "List categories", description = "All categories, each with its attributeSchema.")
    public List<CategoryResponse> listCategories() {
        return categoryMapper.toResponseList(categoryReader.listAll());
    }

    @GetMapping("/categories/{id}")
    @Operation(summary = "Get a category")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Category returned"),
            @ApiResponse(responseCode = "404", description = "Category not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public CategoryResponse getCategory(@PathVariable String id) {
        return categoryMapper.toResponse(categoryReader.getById(parseCategoryId(id)));
    }

    @GetMapping("/products")
    @Operation(summary = "List products", description = "Cursor-based pagination: pass the previous "
            + "response's nextCursor as cursor to fetch the next page. Price filter/sort deferred until "
            + "Phase 2 (Pricing) adds a price field — see PLAN_PHASE1.md.")
    public ProductListResponse listProducts(@RequestParam(required = false) String category,
                                             @RequestParam(required = false) String brand,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(defaultValue = "20") int limit) {
        UUID categoryId = category == null ? null : UUID.fromString(category);
        return productMapper.toListResponse(
                productReader.list(categoryId, brand, productMapper.toCursor(cursor), limit));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Get product detail", description = "Includes attributes, images, derived stock status, and HSN/GST tag.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product returned"),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ProductDetailResponse getProduct(@PathVariable String id) {
        return productMapper.toDetailResponse(productReader.getDetail(parseProductId(id)));
    }

    /**
     * A malformed path-variable id can never match anything, so it's treated as "not found"
     * (matching the pre-Postgres behavior where any non-matching id string was cleanly a 404)
     * rather than falling through to GlobalExceptionHandler's generic 400 for IllegalArgumentException.
     */
    private UUID parseCategoryId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("CATEGORY_NOT_FOUND", "Category not found: " + id);
        }
    }

    private UUID parseProductId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + id);
        }
    }
}
