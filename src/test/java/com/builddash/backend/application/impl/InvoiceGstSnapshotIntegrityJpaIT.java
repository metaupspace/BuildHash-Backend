package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.InvoiceSnapshotBuilder;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderInvoiceSnapshot;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.port.OrderRepository;
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
 * H4.4 Real-PostgreSQL proof: invoice snapshot generation uses the immutable taxRatePercent
 * snapshot persisted at order placement, even if the GST master rate table is modified later.
 */
class InvoiceGstSnapshotIntegrityJpaIT extends AbstractIntegrationTest {

    @Autowired
    private InvoiceSnapshotBuilder invoiceSnapshotBuilder;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID addressId;
    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, phone, created_at, updated_at) VALUES (?, '+919988776655', now(), now())", userId);

        addressId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) VALUES (?, ?, 'HOME', 'Flat 101', 'Mumbai', 'MH', '400001', now(), now())",
                addressId, userId);

        categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Building Materials', ?, 7)",
                categoryId, "mat-" + categoryId);

        productId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) VALUES (?, 'UltraTech Cement 50kg', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "cement-" + productId, categoryId);

        // Initial GST rate is 28.00%
        jdbcTemplate.update("INSERT INTO hsn_gst_rates (hsn_code, description, gst_rate_percent, category, created_at, updated_at) VALUES ('2523', 'Cement', 28.00, 'Materials', now(), now()) ON CONFLICT (hsn_code) DO UPDATE SET gst_rate_percent = 28.00");
    }

    @Test
    void invoiceGeneration_usesPersistedTaxRateSnapshot_evenIfMasterRateChanges() {
        // 1. Order placed: 2 bags @ ₹350.00 base = ₹700.00 subtotal. 28% GST = ₹196.00. Total = ₹896.00.
        // Persisted tax_rate_percent = 28.00
        UUID orderId = UUID.randomUUID();
        UUID slotId = UUID.fromString("11111111-1111-1111-1111-111111111101");
        OrderLineItem lineItem = new OrderLineItem(
                UUID.randomUUID(),
                productId,
                2,
                new BigDecimal("448.00"), // unitFinalPrice (896 / 2)
                new BigDecimal("196.00"), // taxAmount
                new BigDecimal("896.00"), // lineTotal
                new BigDecimal("28.00")   // taxRatePercent
        );

        Order order = new Order(
                orderId,
                userId,
                addressId,
                slotId,
                LocalDate.now(),
                new BigDecimal("896.00"),
                OrderStatus.CONFIRMED,
                null,
                Instant.now(),
                null,
                null,
                List.of(lineItem)
        );
        orderRepository.save(order);

        // 2. Legislative change: GST rate for cement is reduced to 18.00% in the master table
        jdbcTemplate.update("UPDATE hsn_gst_rates SET gst_rate_percent = 18.00 WHERE hsn_code = '2523'");

        // 3. Build invoice snapshot
        OrderInvoiceSnapshot snapshot = invoiceSnapshotBuilder.build(orderId);

        // 4. Assert snapshot reflects historical 28.00% rate, not new 18.00% rate
        assertThat(snapshot.lineItems()).hasSize(1);
        OrderInvoiceSnapshot.InvoiceLineItemSnapshot invoiceLine = snapshot.lineItems().get(0);
        assertThat(invoiceLine.taxRate()).isEqualByComparingTo("28.00");
        assertThat(invoiceLine.taxAmount()).isEqualByComparingTo("196.00");
        assertThat(invoiceLine.lineTotal()).isEqualByComparingTo("896.00");

        // 5. Invariant: subTotal (700) + totalTax (196) == totalAmount (896)
        assertThat(snapshot.subTotal()).isEqualByComparingTo("700.00");
        assertThat(snapshot.totalTax()).isEqualByComparingTo("196.00");
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("896.00");
    }
}
