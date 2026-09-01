package com.builddash.backend.api;

import com.builddash.backend.application.service.ReturnService;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H0.2: the HTTP filter chain gates /returns/{id}/reject and /returns/{id}/qc-pass to
 * VENDOR/ADMIN (see ReturnSecurityMatcherTest); this proves the service layer is its
 * own authority on real data — privileged principals reach the mutation, everyone else
 * gets the existence-hiding 404 even when the return exists.
 */
class ReturnServiceAuthorizationJpaIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ReturnService returnService;
    @Autowired
    private TokenIssuer tokenIssuer;
    @Autowired
    private JdbcTemplate jdbc;

    private String vendorToken;
    private String adminToken;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", customerId);
        vendorToken = "Bearer " + tokenIssuer.issueAccessToken(
                UUID.randomUUID(), UUID.randomUUID(), List.of("VENDOR")).token();
        adminToken = "Bearer " + tokenIssuer.issueAccessToken(
                UUID.randomUUID(), UUID.randomUUID(), List.of("ADMIN")).token();
    }

    @Test
    void vendorRejects_realHttp_returnsRejected() throws Exception {
        UUID returnId = seedDeliveredOrderWithReturn("REQUESTED");

        mockMvc.perform(post("/returns/{id}/reject", returnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isOk());

        assertThat(returnStatus(returnId)).isEqualTo("REJECTED");
    }

    @Test
    void adminRejects_realHttp_returnsRejected() throws Exception {
        UUID returnId = seedDeliveredOrderWithReturn("REQUESTED");

        mockMvc.perform(post("/returns/{id}/reject", returnId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        assertThat(returnStatus(returnId)).isEqualTo("REJECTED");
    }

    @Test
    void vendorQcPass_realHttp_reachesRefundInitiation() throws Exception {
        UUID returnId = seedDeliveredOrderWithReturn("PICKED_UP");

        mockMvc.perform(post("/returns/{id}/qc-pass", returnId)
                        .header(HttpHeaders.AUTHORIZATION, vendorToken))
                .andExpect(status().isOk());

        String status = returnStatus(returnId);
        assertThat(status).isNotEqualTo("PICKED_UP"); // QC transition committed
        Integer refunds = jdbc.queryForObject(
                "SELECT count(*) FROM refunds WHERE return_id = ?", Integer.class, returnId);
        assertThat(refunds).isEqualTo(1); // ...and the refund claim was initiated
    }

    @Test
    void nonPrivilegedDirectServiceCall_getsExistenceHidingNotFound() {
        UUID returnId = seedDeliveredOrderWithReturn("REQUESTED");

        assertThatThrownBy(() -> returnService.reject(returnId, customerId, List.of("USER")))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RETURN_NOT_FOUND");

        assertThatThrownBy(() -> returnService.passQc(returnId, customerId, null))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RETURN_NOT_FOUND");

        assertThat(returnStatus(returnId)).isEqualTo("REQUESTED");
    }

    private String returnStatus(UUID returnId) {
        return jdbc.queryForObject("SELECT status FROM returns WHERE id = ?", String.class, returnId);
    }

    /** Minimal DELIVERED order + SUCCESS payment + a return in the requested state. */
    private UUID seedDeliveredOrderWithReturn(String returnStatus) {
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, name, slug, return_window_days) VALUES (?, 'Hardware', ?, 7)",
                categoryId, "hardware-" + categoryId);
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, name, slug, category_id, status, hsn_code, created_at, updated_at) "
                        + "VALUES (?, 'Cement Bag', ?, ?, 'ACTIVE', '2523', now(), now())",
                productId, "cement-" + productId, categoryId);

        UUID addressId = UUID.randomUUID();
        jdbc.update("INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code, created_at, updated_at) "
                        + "VALUES (?, ?, 'HOME', 'Street 1', 'City', 'MH', '400001', now(), now())",
                addressId, customerId);
        UUID slotId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        jdbc.update("INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_DATE, 1050.00, 'DELIVERED', now(), now())",
                orderId, customerId, addressId, slotId);
        jdbc.update("INSERT INTO order_line_items (id, order_id, product_id, quantity, unit_price, tax_amount, line_total, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 3, 350.00, 294.00, 1050.00, now(), now())",
                UUID.randomUUID(), orderId, productId);
        jdbc.update("INSERT INTO payments (id, order_id, transaction_id, amount, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1050.00, 'SUCCESS', now(), now())",
                UUID.randomUUID(), orderId, "tx_" + orderId);

        UUID returnId = UUID.randomUUID();
        jdbc.update("INSERT INTO returns (id, order_id, user_id, status, reason, photo_keys, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'DAMAGED', '[]'::jsonb, now(), now())",
                returnId, orderId, customerId, returnStatus);
        jdbc.update("INSERT INTO return_line_items (id, return_id, product_id, quantity_requested, refund_amount, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, 350.00, now(), now())",
                UUID.randomUUID(), returnId, productId);
        return returnId;
    }
}
