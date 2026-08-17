package com.builddash.backend.api.controller;

import com.builddash.backend.application.service.CategoryReader;
import com.builddash.backend.application.service.ProductReader;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.ProductDetail;
import com.builddash.backend.domain.model.ProductPage;
import com.builddash.backend.domain.model.ProductPageCursor;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer only: CategoryReader/ProductReader are mocked, so this never touches a real
 * Postgres connection for catalog data. Real query behavior is covered separately (see
 * PROGRESS.md for the checkpoint 6b Postgres integration test).
 */
class CatalogControllerIT extends AbstractIntegrationTest {

    @MockBean
    private CategoryReader categoryReader;

    @MockBean
    private ProductReader productReader;

    @Test
    void listCategories_returnsCategoriesWithoutAuth() throws Exception {
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Cement");
        category.setSlug("cement");
        when(categoryReader.listAll()).thenReturn(List.of(category));

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cement"));
    }

    @Test
    void getCategory_malformedId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/categories/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void getCategory_validButUnknownId_returnsNotFound() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(categoryReader.getById(missingId))
                .thenThrow(new NotFoundException("CATEGORY_NOT_FOUND", "Category not found: " + missingId));

        mockMvc.perform(get("/categories/" + missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void listProducts_returnsItemsAndCursorWithoutAuth() throws Exception {
        UUID categoryId = UUID.randomUUID();
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("UltraTech Cement");
        product.setSlug("ultratech-cement");
        product.setBrand("UltraTech");
        product.setCategoryId(categoryId);
        product.setCreatedAt(Instant.now());
        ProductPageCursor nextCursor = new ProductPageCursor(Instant.now(), product.getId());

        when(productReader.list(eq(categoryId), any(), any(), anyInt()))
                .thenReturn(new ProductPage(List.of(product), nextCursor));

        mockMvc.perform(get("/products").param("category", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(product.getId().toString()))
                .andExpect(jsonPath("$.nextCursor").value(nextCursor.encode()));
    }

    @Test
    void getProduct_happyPath_returnsDetailWithoutAuth() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(productReader.getDetail(productId)).thenReturn(new ProductDetail(
                productId, "UltraTech Cement", "ultratech-cement", categoryId, "Cement", "UltraTech", "2523",
                new BigDecimal("28.00"), Map.of("weightKg", 50), List.of(), true, ProductStatus.ACTIVE
        ));

        mockMvc.perform(get("/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("UltraTech Cement"))
                .andExpect(jsonPath("$.gstRatePercent").value(28.00))
                .andExpect(jsonPath("$.stockStatus").value("in_stock"));
    }

    @Test
    void getProduct_malformedId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/products/not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void getProduct_validButUnknownId_returnsNotFound() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(productReader.getDetail(missingId))
                .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + missingId));

        mockMvc.perform(get("/products/" + missingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
