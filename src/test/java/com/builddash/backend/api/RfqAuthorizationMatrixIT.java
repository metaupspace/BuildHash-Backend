package com.builddash.backend.api;

import com.builddash.backend.application.service.CompanyMembershipService;
import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The 9-B permission matrix end to end, over real HTTP with mutable permission
 * state: every check resolves against the DATABASE at request time, so grants
 * and revocations apply to already-issued tokens without any refresh.
 */
class RfqAuthorizationMatrixIT extends AbstractIntegrationTest {

    /** V26 default profile for PROCUREMENT_MANAGER, minus RFQ_CREATE (revocation fixture). */
    private static final String PM_WITHOUT_RFQ_CREATE = """
            {"permissions": ["COMPANY_VIEW", "RFQ_VIEW", "RFQ_CANCEL", "RFQ_CONVERT",
            "QUOTE_VIEW", "PO_VIEW", "PO_UPLOAD", "PO_CONVERT", "ORDER_VIEW",
            "ORDER_CREATE", "APPROVAL_VIEW"]}""";
    /** V26 default profile for ACCOUNTANT, plus RFQ_CREATE (grant fixture). */
    private static final String ACCOUNTANT_WITH_RFQ_CREATE = """
            {"permissions": ["COMPANY_VIEW", "ORDER_VIEW", "INVOICE_VIEW",
            "STATEMENT_VIEW", "RFQ_CREATE"]}""";
    /** V26 default profile for VIEWER (includes RFQ_VIEW; grant fixture re-adds it). */
    private static final String VIEWER_DEFAULT = """
            {"permissions": ["COMPANY_VIEW", "SITE_VIEW", "ORDER_VIEW", "RFQ_VIEW", "PO_VIEW"]}""";
    private static final String VIEWER_WITHOUT_RFQ_VIEW = """
            {"permissions": ["COMPANY_VIEW", "SITE_VIEW", "ORDER_VIEW", "PO_VIEW"]}""";

    @Autowired
    private CompanyService companyService;
    @Autowired
    private CompanyMembershipService membershipService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private TokenIssuer tokenIssuer;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID otherCompanyId;
    private UUID ownerUserId;
    private UUID pmUserId;
    private UUID accountantUserId;
    private UUID viewerUserId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        ownerUserId = newUser();
        companyId = companyService.create(ownerUserId, "RfqAuthCo", null, null, null).id();
        otherCompanyId = companyService.create(newUser(), "OtherCo", null, null, null).id();
        pmUserId = member(companyId, CompanyRole.PROCUREMENT_MANAGER);
        accountantUserId = member(companyId, CompanyRole.ACCOUNTANT);
        viewerUserId = member(companyId, CompanyRole.VIEWER);

        Category category = new Category();
        category.setName("rfq-auth-cat");
        category.setSlug("rfq-auth-" + UUID.randomUUID());
        UUID categoryId = categoryRepository.save(category).getId();
        Product product = new Product();
        product.setName("rfq-auth-product");
        product.setSlug("rfq-auth-p-" + UUID.randomUUID());
        product.setCategoryId(categoryId);
        product.setHsnCode("6901");
        product.setStatus(ProductStatus.ACTIVE);
        productId = productRepository.save(product).getId();
    }

    // ---- B2B permission matrix (mutable, no token refresh) ----

    @Test
    void defaultProcurementManagerWithRfqCreate_canCreate_201() throws Exception {
        mockMvc.perform(post("/rfq")
                        .header(HttpHeaders.AUTHORIZATION, userToken(pmUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rfqCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void revokedRfqCreate_rejected403_withoutTokenRefresh() throws Exception {
        // Same token before and after the revoke: authorization resolves from DB state.
        String token = userToken(pmUserId);
        assertThatCreateSucceeds(token);

        revokeOrGrant("/companies/{id}/role-permissions/PROCUREMENT_MANAGER", PM_WITHOUT_RFQ_CREATE);

        mockMvc.perform(post("/rfq")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rfqCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void grantedAccountant_canCreate_201() throws Exception {
        mockMvc.perform(post("/rfq")
                        .header(HttpHeaders.AUTHORIZATION, userToken(accountantUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rfqCreateBody()))
                .andExpect(status().isForbidden());

        revokeOrGrant("/companies/{id}/role-permissions/ACCOUNTANT", ACCOUNTANT_WITH_RFQ_CREATE);

        mockMvc.perform(post("/rfq")
                        .header(HttpHeaders.AUTHORIZATION, userToken(accountantUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rfqCreateBody()))
                .andExpect(status().isCreated());
    }

    @Test
    void viewerRfqView_grantAndRevoke_appliesImmediately() throws Exception {
        UUID rfqId = createRfqAsOwner();

        mockMvc.perform(get("/rfq/{id}", rfqId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(viewerUserId)))
                .andExpect(status().isOk()); // default VIEWER profile includes RFQ_VIEW

        revokeOrGrant("/companies/{id}/role-permissions/VIEWER", VIEWER_WITHOUT_RFQ_VIEW);
        mockMvc.perform(get("/rfq/{id}", rfqId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(viewerUserId)))
                .andExpect(status().isForbidden());

        revokeOrGrant("/companies/{id}/role-permissions/VIEWER", VIEWER_DEFAULT);
        mockMvc.perform(get("/rfq/{id}", rfqId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(viewerUserId)))
                .andExpect(status().isOk());
    }

    @Test
    void viewerWithoutRfqCreate_cannotCreate_403() throws Exception {
        mockMvc.perform(post("/rfq")
                        .header(HttpHeaders.AUTHORIZATION, userToken(viewerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rfqCreateBody()))
                .andExpect(status().isForbidden());
    }

    // ---- company scoping ----

    @Test
    void crossCompanyMember_gets404() throws Exception {
        UUID rfqId = createRfqAsOwner();
        UUID stranger = newUser(); // member of OTHER company only

        mockMvc.perform(get("/rfq/{id}", rfqId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    void applicationAdminWithoutMembership_gets404OnRfq() throws Exception {
        UUID rfqId = createRfqAsOwner();
        UUID adminWithoutMembership = newUser();

        mockMvc.perform(get("/rfq/{id}", rfqId)
                        .header(HttpHeaders.AUTHORIZATION,
                                token(adminWithoutMembership, List.of("ADMIN"))))
                .andExpect(status().isNotFound());

        // POST is likewise not company-authorized by the application ADMIN role.
        mockMvc.perform(post("/rfq")
                        .header(HttpHeaders.AUTHORIZATION,
                                token(adminWithoutMembership, List.of("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rfqCreateBody()))
                .andExpect(status().isForbidden());
    }

    // ---- /admin/** separation ----

    @Test
    void companyOwnerWithoutApplicationAdmin_gets403OnAdminVendors() throws Exception {
        mockMvc.perform(get("/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, userToken(ownerUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, userToken(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vendorCreateBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoints_anonymous401() throws Exception {
        mockMvc.perform(get("/admin/vendors"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applicationAdmin_managesVendorsAndSubmitsQuotes() throws Exception {
        // Route the vendor to the RFQ item's actual category, not an arbitrary one.
        UUID categoryId = jdbcTemplate.queryForObject(
                "SELECT category_id FROM products WHERE id = ?", UUID.class, productId);
        String adminToken = token(newUser(), List.of("ADMIN"));

        String vendorBody = mockMvc.perform(post("/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"AdminVendor\",\"categoryIds\":[\"" + categoryId + "\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID vendorId = UUID.fromString(objectMapper.readTree(vendorBody).get("id").asText());

        mockMvc.perform(get("/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        UUID rfqId = createRfqAsOwner();
        mockMvc.perform(post("/admin/rfqs/{rfqId}/quotes", rfqId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendorId\":\"" + vendorId + "\",\"totalAmount\":150.00,"
                                + "\"validUntil\":\"" + Instant.now().plusSeconds(7200) + "\"}"))
                .andExpect(status().isCreated());
    }

    // ---- helpers ----

    private void assertThatCreateSucceeds(String token) throws Exception {
        mockMvc.perform(post("/rfq")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rfqCreateBody()))
                .andExpect(status().isCreated());
    }

    private UUID createRfqAsOwner() throws Exception {
        String body = mockMvc.perform(post("/rfq")
                        .header(HttpHeaders.AUTHORIZATION, userToken(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rfqCreateBody()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private void revokeOrGrant(String pathTemplate, String permissionsBody) throws Exception {
        mockMvc.perform(put(pathTemplate, companyId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(permissionsBody))
                .andExpect(status().isOk());
    }

    private String rfqCreateBody() {
        return "{\"companyId\":\"" + companyId + "\","
                + "\"expiresAt\":\"" + Instant.now().plusSeconds(86400) + "\","
                + "\"notes\":\"matrix\","
                + "\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":10}]}";
    }

    private String vendorCreateBody() {
        return "{\"name\":\"Nope\",\"categoryIds\":[]}";
    }

    private String userToken(UUID userId) {
        return token(userId, List.of("USER"));
    }

    private String token(UUID userId, List<String> roles) {
        return "Bearer " + tokenIssuer.issueAccessToken(userId, UUID.randomUUID(), roles).token();
    }

    private UUID member(UUID company, CompanyRole role) {
        UUID userId = newUser();
        membershipService.addMember(company, ownerUserId, userId, role, List.of());
        return userId;
    }

    private UUID newUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        return userId;
    }
}
