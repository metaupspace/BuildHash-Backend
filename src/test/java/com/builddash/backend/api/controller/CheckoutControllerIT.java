package com.builddash.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.builddash.backend.application.service.AddressService;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.DeliverySlotCounter;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.SlotConfiguration;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.DeliverySlotCounterRepository;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.SlotConfigurationRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckoutControllerIT extends AbstractIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductBasePriceRepository productBasePriceRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private AddressService addressService;

    @Autowired
    private SlotConfigurationRepository slotConfigurationRepository;

    @Autowired
    private DeliverySlotCounterRepository deliverySlotCounterRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createCheckoutIntent_happyPath() throws Exception {
        String phone = "+911111900001";
        JsonNode tokens = loginViaOtp(phone, "Checkout-Device");
        String accessToken = tokens.get("accessToken").asText();
        User user = userRepository.findByPhone(phone).orElseThrow();
        UUID userId = user.getId();

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
        Product savedProduct = productRepository.save(product);

        productBasePriceRepository.save(savedProduct.getId(), new BigDecimal("10.00"));

        // 2. Add items to cart
        cartService.upsertItem(userId, null, savedProduct.getId(), 100, null);

        // 3. Create serviceable address
        Address address = addressService.createAddress(userId, "SITE", "Plot 42", null, "Nagpur", "MH", "440001");

        // 4. Configure delivery slot counter for tomorrow
        SlotConfiguration slot = slotConfigurationRepository.findAllActive().get(0);
        LocalDate slotDate = LocalDate.now().plusDays(1);
        deliverySlotCounterRepository.save(new DeliverySlotCounter(UUID.randomUUID(), slot.id(), slotDate, slot.capacity(), 0));

        // 5. POST /checkout/intent
        String requestBody = String.format("{\"addressId\":\"%s\",\"slotId\":\"%s\",\"slotDate\":\"%s\",\"expectedTotal\":1050.00}",
                address.id(), slot.id(), slotDate);

        mockMvc.perform(post("/checkout/intent")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intentId").exists())
                .andExpect(jsonPath("$.lockedTotal").value(1050.00))
                .andExpect(jsonPath("$.slotId").value(slot.id().toString()))
                .andExpect(jsonPath("$.pricedCart.items.length()").value(1));
    }
}
