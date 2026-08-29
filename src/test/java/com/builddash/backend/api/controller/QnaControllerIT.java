package com.builddash.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QnaControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private TokenIssuer tokenIssuer;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
    void askQuestion_thenAnswer_thenList_answerSourceDefaultsToCustomer() throws Exception {
        UUID productId = saveProduct();
        JsonNode tokens = loginViaOtp("+911111600001", "JUnit-Device");
        String accessToken = tokens.get("accessToken").asText();

        MvcResult askResult = mockMvc.perform(post("/products/" + productId + "/questions")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Is this waterproof?\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        String questionId = objectMapper.readTree(askResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/questions/" + questionId + "/answers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Yes, fully waterproof.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("CUSTOMER"));

        mockMvc.perform(get("/products/" + productId + "/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Is this waterproof?"))
                .andExpect(jsonPath("$[0].answers[0].body").value("Yes, fully waterproof."));
    }

    @Test
    void answerQuestion_unknownQuestion_returnsNotFound() throws Exception {
        JsonNode tokens = loginViaOtp("+911111600002", "JUnit-Device");
        String accessToken = tokens.get("accessToken").asText();

        mockMvc.perform(post("/questions/" + UUID.randomUUID() + "/answers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Yes.\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void answerQuestion_vendorRoleToken_answersAndSourceResolvesVendor() throws Exception {
        // The answers matcher is explicitly hasAnyRole(USER, VENDOR, ADMIN): a vendor token
        // passes the gate AND the issuance-controlled role claim labels the answer VENDOR —
        // labeling and gating read the same trusted claim.
        UUID productId = saveProduct();
        JsonNode tokens = loginViaOtp("+911111600003", "JUnit-Device");
        MvcResult askResult = mockMvc.perform(post("/products/" + productId + "/questions")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"What is the load rating?\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String questionId = objectMapper.readTree(askResult.getResponse().getContentAsString()).get("id").asText();

        // answers.user_id has an FK to users — the vendor identity needs a row; the ROLE
        // claim (not the user row) is what gates and labels.
        UUID vendorUserId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", vendorUserId);
        String vendorToken = "Bearer " + tokenIssuer
                .issueAccessToken(vendorUserId, UUID.randomUUID(), java.util.List.of("VENDOR")).token();

        mockMvc.perform(post("/questions/" + questionId + "/answers")
                        .header("Authorization", vendorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"53 grade, 28-day strength 53 MPa.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("VENDOR"));
    }

    @Test
    void answerQuestion_guestToken_forbiddenByMatcher() throws Exception {
        UUID productId = saveProduct();
        JsonNode tokens = loginViaOtp("+911111600004", "JUnit-Device");
        MvcResult askResult = mockMvc.perform(post("/products/" + productId + "/questions")
                        .header("Authorization", "Bearer " + tokens.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Any warranty?\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String questionId = objectMapper.readTree(askResult.getResponse().getContentAsString()).get("id").asText();

        // Guest tokens are validated against the users row (JwtAuthenticationFilter's
        // guestIdentityStillActive) — no row means 401 before the matcher can 403.
        UUID guestId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, is_guest, created_at, updated_at) VALUES (?, true, now(), now())", guestId);
        String guestToken = "Bearer " + tokenIssuer.issueGuestToken(guestId).token();

        mockMvc.perform(post("/questions/" + questionId + "/answers")
                        .header("Authorization", guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Guests cannot answer.\"}"))
                .andExpect(status().isForbidden());
    }
}
