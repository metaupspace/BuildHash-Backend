package com.builddash.backend.api.controller;

import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.CompanyStatus;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.Address;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Company;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanyRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApprovalControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyMemberRepository memberRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ApprovalRequestRepository approvalRequestRepository;

    private String ownerToken;
    private UUID companyId;
    private UUID approvalId;
    private UUID orderId;

    @BeforeEach
    void setUp() throws Exception {
        String phoneOwner = "+919900220001";
        JsonNode tokensOwner = loginViaOtp(phoneOwner, "Device-Approval-Owner");
        ownerToken = "Bearer " + tokensOwner.get("accessToken").asText();
        UUID ownerId = userRepository.findByPhone(phoneOwner).orElseThrow().getId();

        String phoneMember = "+919900220002";
        loginViaOtp(phoneMember, "Device-Approval-Member");
        UUID memberId = userRepository.findByPhone(phoneMember).orElseThrow().getId();

        Company savedCompany = companyRepository.save(new Company(
                UUID.randomUUID(), "BuildCo", "GST789", "buildco@example.com", "Asia/Kolkata",
                CompanyStatus.ACTIVE, Instant.now(), Instant.now()
        ));
        companyId = savedCompany.id();

        memberRepository.save(new CompanyMember(
                UUID.randomUUID(), companyId, ownerId, CompanyRole.OWNER,
                Instant.now(), Instant.now()
        ));

        memberRepository.save(new CompanyMember(
                UUID.randomUUID(), companyId, memberId, CompanyRole.PROCUREMENT_MANAGER,
                Instant.now(), Instant.now()
        ));

        UUID addressId = addressRepository.save(new Address(
                UUID.randomUUID(), memberId, "OFFICE", "Site 1", null, "City", "State", "400001", 12.34, 56.78, true
        )).id();

        orderId = UUID.randomUUID();
        orderRepository.save(new Order(
                orderId, memberId, addressId, UUID.fromString("11111111-1111-1111-1111-111111111101"),
                LocalDate.now(), new BigDecimal("5000.00"), OrderStatus.PENDING_APPROVAL,
                UUID.randomUUID(), Instant.now(), null, null, List.of()
        ));

        ApprovalRequest savedApproval = approvalRequestRepository.save(new ApprovalRequest(
                UUID.randomUUID(), orderId, companyId, ApprovalRequestStatus.PENDING,
                0, CompanyRole.OWNER, null, Instant.now().plusSeconds(86400),
                new BigDecimal("5000.00"), List.of(), new BigDecimal("1000.00"),
                List.of(), null, List.of(CompanyRole.OWNER), 24, 1,
                Instant.now(), Instant.now()
        ));
        approvalId = savedApproval.id();
    }

    @Test
    void listApprovals_authorizedOwner_returnsList() throws Exception {
        mockMvc.perform(get("/approvals?companyId=" + companyId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(approvalId.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getApprovalDetail_authorized_returnsDetail() throws Exception {
        mockMvc.perform(get("/approvals/" + approvalId)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvalId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectApproval_authorizedOwner_cancelsOrderAndUpdatesStatus() throws Exception {
        mockMvc.perform(post("/approvals/" + approvalId + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Order order = orderRepository.findById(orderId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
    }
}
