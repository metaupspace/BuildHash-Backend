package com.builddash.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductBasePriceRepository productBasePriceRepository;

    private UUID seedPricedProduct(BigDecimal price) {
        Category category = new Category();
        category.setName("Steel");
        category.setSlug("steel-" + UUID.randomUUID());
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName("TMT Bar");
        product.setSlug("tmt-bar-" + UUID.randomUUID());
        product.setCategoryId(savedCategory.getId());
        product.setHsnCode("7214");
        product.setStatus(ProductStatus.ACTIVE);
        Product savedProduct = productRepository.save(product);

        productBasePriceRepository.save(savedProduct.getId(), price);
        return savedProduct.getId();
    }

    @Test
    void cartLifecycle_upsert_read_and_clear() throws Exception {
        UUID productId = seedPricedProduct(new BigDecimal("500.00"));
        JsonNode tokens = loginViaOtp("+911111800001", "Cart-Device");
        String accessToken = tokens.get("accessToken").asText();

        // 1. Get empty cart
        mockMvc.perform(get("/cart").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.finalTotal").value(0));

        // 2. Add item to cart
        String body = "{\"productId\":\"" + productId + "\",\"quantity\":3}";
        mockMvc.perform(put("/cart/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.subtotal").value(1500.0));

        // 3. Clear cart
        mockMvc.perform(delete("/cart").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // 4. Verify empty cart
        mockMvc.perform(get("/cart").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}
