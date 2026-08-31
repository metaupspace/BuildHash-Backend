package com.builddash.backend.api;

import com.builddash.backend.application.service.CompanyMembershipService;
import com.builddash.backend.application.service.CompanyService;
import com.builddash.backend.application.service.PoImportService;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.ProductStatus;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ProductBasePriceRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.TokenIssuer;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.builddash.backend.support.PoTestWorkbooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The 9-C permission matrix over real HTTP: mutable permission state, no token
 * refresh, company scoping, site scope on order attachment, and application-
 * ADMIN separation.
 */
class PoAuthorizationMatrixIT extends AbstractIntegrationTest {

    /** V26 default PROCUREMENT_MANAGER profile minus PO_UPLOAD. */
    private static final String PM_WITHOUT_PO_UPLOAD = """
            {"permissions": ["COMPANY_VIEW", "RFQ_VIEW", "RFQ_CREATE", "RFQ_CANCEL", "RFQ_CONVERT",
            "QUOTE_VIEW", "PO_VIEW", "PO_CONVERT", "ORDER_VIEW", "ORDER_CREATE", "APPROVAL_VIEW"]}""";
    /** V26 default ACCOUNTANT profile plus PO_UPLOAD. */
    private static final String ACCOUNTANT_WITH_PO_UPLOAD = """
            {"permissions": ["COMPANY_VIEW", "ORDER_VIEW", "INVOICE_VIEW", "STATEMENT_VIEW",
            "PO_UPLOAD"]}""";
    /** V26 default VIEWER profile minus PO_VIEW. */
    private static final String VIEWER_WITHOUT_PO_VIEW = """
            {"permissions": ["COMPANY_VIEW", "SITE_VIEW", "ORDER_VIEW", "RFQ_VIEW"]}""";
    /** V26 default VIEWER profile (PO_VIEW restored). */
    private static final String VIEWER_DEFAULT = """
            {"permissions": ["COMPANY_VIEW", "SITE_VIEW", "ORDER_VIEW", "RFQ_VIEW", "PO_VIEW"]}""";
    /** V26 default PM profile minus PO_CONVERT only. */
    private static final String PM_WITHOUT_PO_CONVERT = """
            {"permissions": ["COMPANY_VIEW", "RFQ_VIEW", "RFQ_CREATE", "RFQ_CANCEL", "RFQ_CONVERT",
            "QUOTE_VIEW", "PO_VIEW", "PO_UPLOAD", "ORDER_VIEW", "ORDER_CREATE", "APPROVAL_VIEW"]}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CompanyService companyService;
    @Autowired
    private CompanyMembershipService membershipService;
    @Autowired
    private PoImportService poImportService;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductBasePriceRepository productBasePriceRepository;
    @Autowired
    private TokenIssuer tokenIssuer;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID companyId;
    private UUID ownerUserId;
    private UUID pmUserId;
    private UUID accountantUserId;
    private UUID viewerUserId;
    private UUID productId;
    private String slug;
    private UUID addressId;

    @BeforeEach
    void setUp() {
        ownerUserId = newUser();
        companyId = companyService.create(ownerUserId, "PoAuthCo", null, null, null).id();
        pmUserId = member(CompanyRole.PROCUREMENT_MANAGER);
        accountantUserId = member(CompanyRole.ACCOUNTANT);
        viewerUserId = member(CompanyRole.VIEWER);
        addressId = jdbcTemplate.queryForObject(
                "INSERT INTO addresses (id, user_id, type, line1, city, state, zip_code) "
                        + "VALUES (?, ?, 'SITE', 'Plot 1', 'Nagpur', 'MH', '440001') RETURNING id",
                UUID.class, ownerUserId, ownerUserId);

        Category category = new Category();
        category.setName("po-auth-cat");
        category.setSlug("po-auth-" + UUID.randomUUID());
        UUID categoryId = categoryRepository.save(category).getId();
        Product product = new Product();
        slug = "po-auth-" + UUID.randomUUID();
        product.setName("po-auth-product");
        product.setSlug(slug);
        product.setCategoryId(categoryId);
        product.setHsnCode("6901");
        product.setStatus(ProductStatus.ACTIVE);
        productId = productRepository.save(product).getId();
        productBasePriceRepository.save(productId, new BigDecimal("10.00"));
    }

    // ---- PO_UPLOAD on /po/bulk ----

    @Test
    void defaultProcurementManager_canBulkUpload_201() throws Exception {
        bulkUpload(userToken(pmUserId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REVIEW"));
    }

    @Test
    void revokedPoUpload_403_withoutTokenRefresh() throws Exception {
        String token = userToken(pmUserId);
        bulkUpload(token).andExpect(status().isCreated());

        revokeOrGrant("PROCUREMENT_MANAGER", PM_WITHOUT_PO_UPLOAD);

        bulkUpload(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void grantedAccountant_canBulkUpload_201() throws Exception {
        bulkUpload(userToken(accountantUserId)).andExpect(status().isForbidden());

        revokeOrGrant("ACCOUNTANT", ACCOUNTANT_WITH_PO_UPLOAD);

        bulkUpload(userToken(accountantUserId)).andExpect(status().isCreated());
    }

    @Test
    void bulkUpload_requiresIdempotencyKey() throws Exception {
        mockMvc.perform(multipart("/po/bulk")
                        .file(xlsx())
                        .param("companyId", companyId.toString())
                        .header(HttpHeaders.AUTHORIZATION, userToken(pmUserId)))
                .andExpect(status().isBadRequest());
    }

    // ---- PO_VIEW on GET /po/imports/{id} ----

    @Test
    void viewerPoView_grantAndRevoke_appliesImmediately() throws Exception {
        UUID importId = createImportAsOwner();

        mockMvc.perform(get("/po/imports/{id}", importId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(viewerUserId)))
                .andExpect(status().isOk()); // default VIEWER profile includes PO_VIEW

        revokeOrGrant("VIEWER", VIEWER_WITHOUT_PO_VIEW);
        mockMvc.perform(get("/po/imports/{id}", importId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(viewerUserId)))
                .andExpect(status().isForbidden());

        revokeOrGrant("VIEWER", VIEWER_DEFAULT);
        mockMvc.perform(get("/po/imports/{id}", importId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(viewerUserId)))
                .andExpect(status().isOk());
    }

    // ---- company scoping and separation ----

    @Test
    void crossCompany_404() throws Exception {
        UUID importId = createImportAsOwner();
        UUID stranger = newUser(); // no membership at all

        mockMvc.perform(get("/po/imports/{id}", importId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    void applicationAdminWithoutMembership_404_onB2bPoEndpoints() throws Exception {
        UUID importId = createImportAsOwner();
        UUID admin = newUser();

        mockMvc.perform(get("/po/imports/{id}", importId)
                        .header(HttpHeaders.AUTHORIZATION, token(admin, List.of("ADMIN"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(multipart("/po/bulk")
                        .file(xlsx())
                        .param("companyId", companyId.toString())
                        .header("Idempotency-Key", "admin-key")
                        .header(HttpHeaders.AUTHORIZATION, token(admin, List.of("ADMIN"))))
                .andExpect(status().isForbidden()); // ADMIN is not a company member

        // POST is gated by the blanket POST->USER security rule first (same as
        // /rfq in 9-B): a pure application-ADMIN token never reaches the service.
        mockMvc.perform(post("/po/imports/{id}/convert", importId)
                        .header(HttpHeaders.AUTHORIZATION, token(admin, List.of("ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void companyOwnerWithoutApplicationAdmin_403OnAdmin() throws Exception {
        mockMvc.perform(get("/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, userToken(ownerUserId)))
                .andExpect(status().isForbidden());
    }

    // ---- PO_UPLOAD on /orders/{id}/po ----

    @Test
    void procurementManager_canAttach_201() throws Exception {
        UUID orderId = b2bOrder(null);

        mockMvc.perform(multipart("/orders/{id}/po", orderId)
                        .file(xlsx())
                        .header(HttpHeaders.AUTHORIZATION, userToken(pmUserId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("STORED"));
    }

    @Test
    void siteScopedMember_outsideOrderSite_403() throws Exception {
        UUID siteA = createSite();
        UUID siteB = createSite();
        UUID orderId = b2bOrder(siteA);

        // SITE_SUPERVISOR scoped to siteB only, granted PO_UPLOAD
        jdbcTemplate.update("INSERT INTO company_role_permissions (company_id, role, permission) "
                        + "VALUES (?, 'SITE_SUPERVISOR', 'PO_UPLOAD') ON CONFLICT DO NOTHING",
                companyId);
        UUID supervisor = newUser();
        UUID memberId = jdbcTemplate.queryForObject(
                "INSERT INTO company_members (id, company_id, user_id, role) "
                        + "VALUES (?, ?, ?, 'SITE_SUPERVISOR') RETURNING id",
                UUID.class, UUID.randomUUID(), companyId, supervisor);
        jdbcTemplate.update("INSERT INTO company_site_assignments (member_id, site_id) VALUES (?, ?)",
                memberId, siteB);

        mockMvc.perform(multipart("/orders/{id}/po", orderId)
                        .file(xlsx())
                        .header(HttpHeaders.AUTHORIZATION, userToken(supervisor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SITE_OUT_OF_SCOPE"));
    }

    // ---- PO_CONVERT ----

    @Test
    void procurementManager_canConvert() throws Exception {
        UUID importId = createImportAsOwner();

        mockMvc.perform(post("/po/imports/{id}/convert", importId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(pmUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"))
                .andExpect(jsonPath("$.draftCartId").exists());
    }

    @Test
    void revokedPoConvert_403() throws Exception {
        UUID importId = createImportAsOwner();
        revokeOrGrant("PROCUREMENT_MANAGER", PM_WITHOUT_PO_CONVERT);

        mockMvc.perform(post("/po/imports/{id}/convert", importId)
                        .header(HttpHeaders.AUTHORIZATION, userToken(pmUserId)))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions bulkUpload(String bearer)
            throws Exception {
        return mockMvc.perform(multipart("/po/bulk")
                .file(xlsx())
                .param("companyId", companyId.toString())
                .header("Idempotency-Key", "key-" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private MockMultipartFile xlsx() {
        return new MockMultipartFile("file", "po.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                PoTestWorkbooks.workbook(new Object[][]{
                        {"sku", "quantity"},
                        {slug, 10}
                }));
    }

    private UUID createImportAsOwner() {
        return poImportService.importWorkbook(ownerUserId, companyId, "key-" + UUID.randomUUID(),
                        new MockMultipartFile("file", "po.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                PoTestWorkbooks.workbook(new Object[][]{
                                        {"sku", "quantity"},
                                        {slug, 10}
                                })))
                .poImport().id();
    }

    private UUID b2bOrder(UUID siteId) {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO orders (id, user_id, address_id, slot_id, slot_date, total_amount, "
                        + "status, company_id, site_id, delivery_slot_lock_id) VALUES (?, ?, ?, ?, ?, 100.00, "
                        + "'PAYMENT_PENDING', ?, ?, ?)",
                orderId, ownerUserId, addressId, UUID.randomUUID(), LocalDate.now(), companyId, siteId, UUID.randomUUID());
        return orderId;
    }

    private UUID createSite() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO company_sites (id, company_id, name) VALUES (?, ?, ?) RETURNING id",
                UUID.class, UUID.randomUUID(), companyId, "Site-" + UUID.randomUUID());
    }

    private void revokeOrGrant(String role, String permissionsBody) throws Exception {
        mockMvc.perform(put("/companies/{id}/role-permissions/{role}", companyId, role)
                        .header(HttpHeaders.AUTHORIZATION, userToken(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(permissionsBody))
                .andExpect(status().isOk());
    }

    private String userToken(UUID userId) {
        return token(userId, List.of("USER"));
    }

    private String token(UUID userId, List<String> roles) {
        return "Bearer " + tokenIssuer.issueAccessToken(userId, UUID.randomUUID(), roles).token();
    }

    private UUID member(CompanyRole role) {
        UUID userId = newUser();
        membershipService.addMember(companyId, ownerUserId, userId, role, List.of());
        return userId;
    }

    private UUID newUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, created_at, updated_at) VALUES (?, now(), now())", userId);
        return userId;
    }
}
