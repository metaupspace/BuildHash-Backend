package com.builddash.backend.api.controller;

import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the guest-cart contract end to end against real Postgres (FKs enforced):
 * guest identity is persisted as a users row, OTP login merges the guest cart into
 * the real account, and the merged guest token is dead afterwards.
 */
class GuestCartMergeIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductBasePriceRepository productBasePriceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void guestAddCartItem_persistsAgainstRealUsersRow() throws Exception {
        String guestToken = guestToken();
        Product product = seededProduct();
        UUID guestUserId = subjectOf(guestToken);

        mockMvc.perform(put("/cart/items")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + product.getId() + "\",\"quantity\":2}"))
                .andExpect(status().isOk());

        // Pre-fix this INSERT violated carts.user_id -> users(id); now the join must hold
        Integer cartsWithRealUser = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM carts c JOIN users u ON u.id = c.user_id WHERE u.id = ?",
                Integer.class, guestUserId);
        assertThat(cartsWithRealUser).isEqualTo(1);
        assertThat(userRepository.findById(guestUserId)).isPresent();
    }

    @Test
    void mergeOnLogin_noExistingCart_reassignsGuestCartToRealUser() throws Exception {
        String guestToken = guestToken();
        Product product = seededProduct();
        guestAddsItem(guestToken, product.getId(), 2);

        String phone = "+911111910101";
        JsonNode tokens = verifyOtpAsGuest(phone, guestToken);
        String accessToken = tokens.get("accessToken").asText();
        UUID realUserId = userRepository.findByPhone(phone).orElseThrow().getId();

        mockMvc.perform(get("/cart").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(realUserId.toString()))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(product.getId().toString()))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        // Guest users row is retired, not deleted — FK dependents stay intact
        UUID guestUserId = subjectOf(guestToken);
        String mergedInto = jdbcTemplate.queryForObject(
                "SELECT merged_into_user_id FROM users WHERE id = ?", String.class, guestUserId);
        assertThat(mergedInto).isEqualTo(realUserId.toString());
    }

    @Test
    void mergeOnLogin_existingCartAbsorbsGuestItems() throws Exception {
        Product productA = seededProduct();
        Product productB = seededProduct();
        String phone = "+911111910202";

        // Real user already has a cart with productA x1
        JsonNode realTokens = loginViaOtp(phone, "Real-Device");
        String realToken = realTokens.get("accessToken").asText();
        mockMvc.perform(put("/cart/items")
                        .header("Authorization", "Bearer " + realToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productA.getId() + "\",\"quantity\":1}"))
                .andExpect(status().isOk());

        // Guest gathers productA x2 + productB x1
        String guestToken = guestToken();
        guestAddsItem(guestToken, productA.getId(), 2);
        guestAddsItem(guestToken, productB.getId(), 1);

        verifyOtpAsGuest(phone, guestToken);

        // Decided strategy: line items merge — duplicates sum, guest-only items copied
        MvcResult cartResult = mockMvc.perform(get("/cart").header("Authorization", "Bearer " + realToken))
                .andExpect(status().isOk())
                .andReturn();
        String cartBody = cartResult.getResponse().getContentAsString();
        assertThat((java.util.List<Integer>) JsonPath.read(cartBody,
                String.format("$.items[?(@.productId == '%s')].quantity", productA.getId()))).containsExactly(3);
        assertThat((java.util.List<Integer>) JsonPath.read(cartBody,
                String.format("$.items[?(@.productId == '%s')].quantity", productB.getId()))).containsExactly(1);

        // Guest cart is discarded after its items are absorbed
        Integer guestCarts = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM carts WHERE user_id = ?", Integer.class, subjectOf(guestToken));
        assertThat(guestCarts).isZero();
    }

    @Test
    void mergedGuestToken_isRejectedEverywhereAfterMerge() throws Exception {
        String guestToken = guestToken();
        Product product = seededProduct();
        guestAddsItem(guestToken, product.getId(), 1);

        verifyOtpAsGuest("+911111910303", guestToken);

        // Spec: invalidate the guest token — a stale merged token must not authenticate,
        // not even against the one surface guests could write to (/cart/**)
        mockMvc.perform(put("/cart/items")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + product.getId() + "\",\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
    }


    private String guestToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/guest"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private void guestAddsItem(String guestToken, UUID productId, int quantity) throws Exception {
        mockMvc.perform(put("/cart/items")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}"))
                .andExpect(status().isOk());
    }

    /**
     * OTP verify carrying the guest's access token in the Authorization header — the
     * client-facing contract for "merge my guest cart into the account I'm logging into".
     */
    private JsonNode verifyOtpAsGuest(String phone, String guestToken) throws Exception {
        mockMvc.perform(post("/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk());
        String otp = smsGateway.lastOtpFor(phone);
        MvcResult result = mockMvc.perform(post("/auth/otp/verify")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"otp\":\"" + otp + "\",\"deviceFingerprint\":\"GuestMerge-Device\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Product seededProduct() {
        Category category = new Category();
        category.setName("Bricks");
        category.setSlug("bricks-" + UUID.randomUUID());
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Red Brick");
        product.setSlug("red-brick-" + UUID.randomUUID());
        product.setCategoryId(savedCategory.getId());
        product.setHsnCode("6901");
        product.setStatus(ProductStatus.ACTIVE);
        Product saved = productRepository.save(product);
        productBasePriceRepository.save(saved.getId(), new BigDecimal("10.00"));
        return saved;
    }

    private UUID subjectOf(String jwt) throws java.io.IOException {
        String payload = jwt.split("\\.")[1];
        JsonNode claims = objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
        return UUID.fromString(claims.get("sub").asText());
    }
}
