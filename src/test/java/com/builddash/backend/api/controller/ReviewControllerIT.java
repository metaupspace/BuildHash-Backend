package com.builddash.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private UUID saveProduct() {
        Category category = new Category();
        category.setName("Cement");
        category.setSlug("cement-" + UUID.randomUUID());
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Product");
        product.setSlug("product-" + UUID.randomUUID());
        product.setCategoryId(savedCategory.getId());
        product.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(product).getId();
    }

    @Test
    void submitReview_thenListReviews_returnsSubmittedReview() throws Exception {
        UUID productId = saveProduct();
        JsonNode tokens = loginViaOtp("+911111500001", "JUnit-Device");
        String accessToken = tokens.get("accessToken").asText();

        mockMvc.perform(post("/products/" + productId + "/reviews")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Great product\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));

        mockMvc.perform(get("/products/" + productId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comment").value("Great product"));
    }

    @Test
    void submitReview_withoutToken_returnsUnauthorized() throws Exception {
        UUID productId = saveProduct();

        mockMvc.perform(post("/products/" + productId + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Great product\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitReview_unknownProduct_returnsNotFound() throws Exception {
        JsonNode tokens = loginViaOtp("+911111500002", "JUnit-Device");
        String accessToken = tokens.get("accessToken").asText();

        mockMvc.perform(post("/products/" + UUID.randomUUID() + "/reviews")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Great product\"}"))
                .andExpect(status().isNotFound());
    }
}
