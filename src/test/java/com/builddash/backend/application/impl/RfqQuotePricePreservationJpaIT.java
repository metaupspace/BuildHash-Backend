package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.OrderResult;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.application.service.RfqService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.RfqQuoteStatus;
import com.builddash.backend.domain.enums.RfqStatus;
import com.builddash.backend.domain.model.Cart;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Rfq;
import com.builddash.backend.domain.model.RfqQuote;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.RfqQuoteRepository;
import com.builddash.backend.domain.port.RfqRepository;
import com.builddash.backend.domain.port.VendorRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H4.3 Real-PostgreSQL proof: accepted RFQ quotes preserve exact commercial pricing
 * through B2B draft cart creation and order checkout, without silently reverting to catalog pricing.
 */
class RfqQuotePricePreservationJpaIT extends AbstractIntegrationTest {

    @Autowired
    private RfqService rfqService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RfqRepository rfqRepository;

    @Autowired
    private RfqQuoteRepository rfqQuoteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID companyId;
    private UUID siteId;
    private UUID vendorId;
    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);

        companyId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO companies (id, name, status, created_at, updated_at) VALUES (?, 'Acme Builders', 'ACTIVE', now(), now())", companyId);

        // Member is OWNER
        jdbcTemplate.update("INSERT INTO company_members (id, company_id, user_id, role, created_at, updated_at) VALUES (gen_random_uuid(), ?, ?, 'OWNER', now(), now())",
                companyId, userId);

        siteId = UUID.randomUUID();
        UUID siteAddressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable, created_at, updated_at) VALUES (?, ?, 'OFFICE', '123 Site Road', 'City', 'MH', '400001', true, now(), now())",
                siteAddressId, userId);
        jdbcTemplate.update("INSERT INTO company_sites (id, company_id, name, address_id, active, created_at, updated_at) VALUES (?, ?, 'Site Alpha', ?, true, now(), now())",
                siteId, companyId, siteAddressId);

        categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Steel', ?, 7)",
                categoryId, "steel-" + categoryId);

        vendorId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO vendors (id, name, active, created_at, updated_at) VALUES (?, 'Steel Suppliers Ltd', true, now(), now())", vendorId);
        jdbcTemplate.update("INSERT INTO vendor_categories (vendor_id, category_id) VALUES (?, ?)", vendorId, categoryId);

        productId = UUID.randomUUID();
        // Catalog price is ₹10,000.00
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'Steel Beam 10m', ?, ?, 'ACTIVE', '7208', now(), now())",
                productId, "beam-" + productId, categoryId);
        jdbcTemplate.update("INSERT INTO product_base_prices (product_id, price, created_at, updated_at) VALUES (?, 10000.00, now(), now())", productId);
        jdbcTemplate.update("INSERT INTO hsn_gst_rates (hsn_code, description, gst_rate_percent, category, created_at, updated_at) VALUES ('7208', 'Steel', 18.00, 'Steel', now(), now()) ON CONFLICT (hsn_code) DO NOTHING");
    }

    @Test
    void rfqConversion_preservesNegotiatedQuotePriceThroughCheckout() {
        // 1. Create RFQ for 10 Steel Beams (catalog price 10 x 10,000 = 100,000 + 18% GST = 118,000)
        Rfq rfq = rfqService.create(userId, companyId, Instant.now().plusSeconds(86400), "Urgent beam order",
                List.of(new RfqService.ItemCommand(productId, 10)));

        // 2. Vendor routes and submits negotiated quote for ₹70,000.00 base (₹7,000/beam instead of ₹10,000)
        RfqQuote quote = rfqService.submitQuote(rfq.id(), vendorId, new BigDecimal("70000.00"),
                Instant.now().plusSeconds(86400));
        UUID quoteId = quote.id();

        // 3. Buyer converts accepted quote into B2B draft cart
        RfqService.ConversionResult result = rfqService.convert(userId, rfq.id(), quoteId);
        assertThat(result.rfq().status()).isEqualTo(RfqStatus.CONVERTED);
        UUID draftCartId = result.cartId();

        // 4. Verify draft cart line items have unitPriceOverride = 7000.00
        Cart draftCart = cartRepository.findById(draftCartId).orElseThrow();
        assertThat(draftCart.items()).hasSize(1);
        assertThat(draftCart.items().get(0).unitPriceOverride()).isEqualByComparingTo("7000.00");

        // 5. Checkout the B2B draft cart
        UUID addressId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, is_serviceable, created_at, updated_at) VALUES (?, ?, 'OFFICE', '456 Commercial St', 'City', 'MH', '400001', true, now(), now())",
                addressId, userId);
        jdbcTemplate.update("INSERT INTO delivery_slot_counters (id, slot_id, slot_date, capacity, current_count) VALUES (gen_random_uuid(), ?, CURRENT_DATE, 10, 1) ON CONFLICT DO NOTHING", slotId);

        // Negotiated base = ₹70,000.00 + 18% GST (₹12,600.00) = ₹82,600.00 (NOT catalog ₹118,000.00)
        BigDecimal expectedTotal = new BigDecimal("82600.00");
        String idempotencyKey = "rfq_checkout_" + UUID.randomUUID();

        OrderResult orderResult = orderService.create(userId, addressId, slotId, LocalDate.now(),
                expectedTotal, draftCartId, siteId, idempotencyKey);

        Order order = orderResult.order();
        assertThat(order.totalAmount()).isEqualByComparingTo("82600.00");

        OrderLineItem lineItem = order.lineItems().get(0);
        assertThat(lineItem.taxAmount()).isEqualByComparingTo("12600.00");
        assertThat(lineItem.lineTotal()).isEqualByComparingTo("82600.00");
        assertThat(lineItem.taxRatePercent()).isEqualByComparingTo("18.00");
    }
}
