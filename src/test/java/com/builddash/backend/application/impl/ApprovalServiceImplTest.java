package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.ApprovalDecidedEvent;
import com.builddash.backend.application.event.OrderCancelledEvent;
import com.builddash.backend.application.service.ApprovalEligibilityResolver;
import com.builddash.backend.application.service.ApprovalService;
import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.DeliverySlotService;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.domain.enums.ApprovalActionType;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.ForbiddenException;
import com.builddash.backend.domain.exception.InvalidApprovalStateException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.SlotUnavailableException;
import com.builddash.backend.domain.model.ApprovalAction;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.CompanyMember;
import com.builddash.backend.domain.model.DeliverySlotLock;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.ApprovalActionRepository;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.CompanyMemberRepository;
import com.builddash.backend.domain.port.CompanySiteAssignmentRepository;
import com.builddash.backend.domain.port.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceImplTest {

    @Mock
    private B2bAuthorizer b2bAuthorizer;
    @Mock
    private ApprovalRequestRepository approvalRequestRepository;
    @Mock
    private ApprovalActionRepository approvalActionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CompanyMemberRepository memberRepository;
    @Mock
    private CompanySiteAssignmentRepository siteAssignmentRepository;
    @Mock
    private ApprovalEligibilityResolver eligibilityResolver;
    @Mock
    private DeliverySlotService deliverySlotService;
    @Mock
    private OrderService orderService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ApprovalServiceImpl approvalService;

    private final UUID approvalId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID placerUserId = UUID.randomUUID();
    private final UUID approverUserId = UUID.randomUUID();
    private final UUID approverMemberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<?> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private Order pendingApprovalOrder() {
        return new Order(orderId, placerUserId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("150.00"), OrderStatus.PENDING_APPROVAL, null, Instant.now(),
                null, null, List.of(), companyId, null, null);
    }

    private ApprovalRequest pendingRequest() {
        return new ApprovalRequest(approvalId, orderId, companyId, ApprovalRequestStatus.PENDING,
                0, CompanyRole.PROCUREMENT_MANAGER, null,
                Instant.now().plus(Duration.ofHours(24)), new BigDecimal("150.00"),
                List.of(), new BigDecimal("100.00"), List.of(), null,
                List.of(CompanyRole.PROCUREMENT_MANAGER, CompanyRole.OWNER), 24, 1, null, null);
    }

    private CompanyMember approverMember() {
        return new CompanyMember(approverMemberId, companyId, approverUserId,
                CompanyRole.PROCUREMENT_MANAGER, null, null);
    }

    private void stubHappyPath(ApprovalRequest request, Order order) {
        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(approverMember()));
        when(eligibilityResolver.eligibleApprovers(eq(companyId), eq(request.currentRole()),
                any(), eq(placerUserId)))
                .thenReturn(List.of(approverMember()));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(approvalActionRepository.findByRequestId(approvalId)).thenReturn(List.of());
    }

    @Test
    void approve_happyPath_resumesPaymentAndInitiatesOutsideTx() {
        ApprovalRequest request = pendingRequest();
        Order order = pendingApprovalOrder();
        stubHappyPath(request, order);
        DeliverySlotLock lock = new DeliverySlotLock(UUID.randomUUID(), placerUserId, order.slotId(),
                order.slotDate(), Instant.now().plus(Duration.ofMinutes(15)),
                com.builddash.backend.domain.enums.DeliverySlotLockStatus.ACTIVE);
        when(deliverySlotService.acquireLock(eq(placerUserId), eq(order.slotId()),
                eq(order.slotDate()), any(Duration.class))).thenReturn(lock);

        ApprovalService.ApprovalDetail result = approvalService.approve(approverUserId, approvalId);

        verify(b2bAuthorizer).authorize(approverUserId, companyId, CompanyPermission.APPROVAL_ACT, null, true);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().status()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(orderCaptor.getValue().deliverySlotLockId()).isEqualTo(lock.id());

        ArgumentCaptor<ApprovalRequest> requestCaptor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().status()).isEqualTo(ApprovalRequestStatus.APPROVED);

        ArgumentCaptor<ApprovalAction> actionCaptor = ArgumentCaptor.forClass(ApprovalAction.class);
        verify(approvalActionRepository).save(actionCaptor.capture());
        assertThat(actionCaptor.getValue().type()).isEqualTo(ApprovalActionType.APPROVED);
        assertThat(actionCaptor.getValue().actorMemberId()).isEqualTo(approverMemberId);
        assertThat(actionCaptor.getValue().stageIndex()).isZero();

        verify(eventPublisher).publishEvent(any(ApprovalDecidedEvent.class));
        verify(orderService).initiatePaymentForApprovedOrder(orderId);
        assertThat(result.request().status()).isEqualTo(ApprovalRequestStatus.APPROVED);
    }

    @Test
    void approve_orderPlacer_throws403EvenWhenPlacerIsEligibleApprover() {
        ApprovalRequest request = pendingRequest();
        Order order = pendingApprovalOrder();
        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(memberRepository.findByCompanyIdAndUserId(companyId, placerUserId))
                .thenReturn(Optional.of(new CompanyMember(approverMemberId, companyId, placerUserId,
                        CompanyRole.PROCUREMENT_MANAGER, null, null)));

        assertThatThrownBy(() -> approvalService.approve(placerUserId, approvalId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_APPROVAL_PROHIBITED");

        verify(orderRepository, never()).save(any());
        verify(orderService, never()).initiatePaymentForApprovedOrder(any());
    }

    @Test
    void approve_notEligibleForStage_throws403() {
        ApprovalRequest request = pendingRequest();
        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(pendingApprovalOrder()));
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(approverMember()));
        when(eligibilityResolver.eligibleApprovers(any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> approvalService.approve(approverUserId, approvalId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_INELIGIBLE");
    }

    @Test
    void approve_assignedToAnotherMember_throws403() {
        ApprovalRequest request = pendingRequest().assign(UUID.randomUUID());
        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(pendingApprovalOrder()));
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(approverMember()));
        when(eligibilityResolver.eligibleApprovers(any(), any(), any(), any()))
                .thenReturn(List.of(approverMember()));

        assertThatThrownBy(() -> approvalService.approve(approverUserId, approvalId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_DELEGATED_TO_OTHER");
    }

    @Test
    void approve_terminalRequest_throws409() {
        ApprovalRequest request = pendingRequest().approve();
        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(pendingApprovalOrder()
                .resumePayment(UUID.randomUUID())));

        assertThatThrownBy(() -> approvalService.approve(approverUserId, approvalId))
                .isInstanceOf(InvalidApprovalStateException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_NOT_PENDING");
    }

    @Test
    void approve_orderNotPendingApproval_throws409() {
        ApprovalRequest request = pendingRequest();
        Order order = new Order(orderId, placerUserId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("150.00"), OrderStatus.PAYMENT_PENDING, UUID.randomUUID(), Instant.now(),
                null, null, List.of(), companyId, null, null);
        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> approvalService.approve(approverUserId, approvalId))
                .isInstanceOf(InvalidApprovalStateException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_NOT_PENDING_APPROVAL");
    }

    @Test
    void approve_slotUnavailable_commitsCancellationAndSurfacesPostTx() {
        ApprovalRequest request = pendingRequest();
        Order order = pendingApprovalOrder();
        stubHappyPath(request, order);
        when(deliverySlotService.acquireLock(any(), any(), any(), any()))
                .thenThrow(new SlotUnavailableException("SLOT_CAPACITY_EXCEEDED", "no capacity"));

        assertThatThrownBy(() -> approvalService.approve(approverUserId, approvalId))
                .isInstanceOf(SlotUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", "SLOT_CAPACITY_EXCEEDED");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().status()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<ApprovalRequest> requestCaptor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().status()).isEqualTo(ApprovalRequestStatus.CANCELLED);

        ArgumentCaptor<ApprovalAction> actionCaptor = ArgumentCaptor.forClass(ApprovalAction.class);
        verify(approvalActionRepository).save(actionCaptor.capture());
        assertThat(actionCaptor.getValue().type()).isEqualTo(ApprovalActionType.CANCELLED);
        assertThat(actionCaptor.getValue().detail()).isEqualTo("APPROVAL_SLOT_UNAVAILABLE");

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().origin())
                .isEqualTo(OrderCancelledEvent.OrderCancellationOrigin.APPROVAL_SLOT_UNAVAILABLE);

        verify(orderService, never()).initiatePaymentForApprovedOrder(any());
    }

    @Test
    void reject_happyPath_cancelsOrderWithoutSlotOrPayment() {
        ApprovalRequest request = pendingRequest();
        Order order = pendingApprovalOrder();
        stubHappyPath(request, order);

        approvalService.reject(approverUserId, approvalId);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().status()).isEqualTo(OrderStatus.CANCELLED);

        ArgumentCaptor<ApprovalRequest> requestCaptor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(approvalRequestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().status()).isEqualTo(ApprovalRequestStatus.REJECTED);

        verify(approvalActionRepository).save(any(ApprovalAction.class));
        verify(deliverySlotService, never()).acquireLock(any(), any(), any(), any());
        verifyNoInteractions(orderService);

        var events = org.mockito.Mockito.mockingDetails(eventPublisher).getInvocations().stream()
                .map(inv -> inv.getArgument(0))
                .toList();
        assertThat(events).anyMatch(e -> e instanceof OrderCancelledEvent oc
                && oc.origin() == OrderCancelledEvent.OrderCancellationOrigin.APPROVAL_REJECTED);
        assertThat(events).anyMatch(e -> e instanceof ApprovalDecidedEvent d && !d.approved());
    }

    @Test
    void reject_orderPlacer_throws403() {
        ApprovalRequest request = pendingRequest();
        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(pendingApprovalOrder()));
        when(memberRepository.findByCompanyIdAndUserId(companyId, placerUserId))
                .thenReturn(Optional.of(new CompanyMember(approverMemberId, companyId, placerUserId,
                        CompanyRole.PROCUREMENT_MANAGER, null, null)));

        assertThatThrownBy(() -> approvalService.reject(placerUserId, approvalId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_APPROVAL_PROHIBITED");
    }

    @Test
    void delegate_happyPath_assignsOnceAndRecordsAction() {
        ApprovalRequest request = pendingRequest();
        Order order = pendingApprovalOrder();
        UUID delegateMemberId = UUID.randomUUID();
        CompanyMember delegate = new CompanyMember(delegateMemberId, companyId, UUID.randomUUID(),
                CompanyRole.PROCUREMENT_MANAGER, null, null);

        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(approverMember()));
        when(memberRepository.findById(delegateMemberId)).thenReturn(Optional.of(delegate));
        when(eligibilityResolver.eligibleApprovers(eq(companyId), eq(CompanyRole.PROCUREMENT_MANAGER),
                any(), eq(placerUserId))).thenReturn(List.of(approverMember(), delegate));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalActionRepository.findByRequestId(approvalId)).thenReturn(List.of());

        ApprovalService.ApprovalDetail result = approvalService.delegate(approverUserId, approvalId, delegateMemberId);

        verify(b2bAuthorizer).authorize(approverUserId, companyId, CompanyPermission.APPROVAL_DELEGATE, null, true);
        assertThat(result.request().assignedMemberId()).isEqualTo(delegateMemberId);

        ArgumentCaptor<ApprovalAction> actionCaptor = ArgumentCaptor.forClass(ApprovalAction.class);
        verify(approvalActionRepository).save(actionCaptor.capture());
        assertThat(actionCaptor.getValue().type()).isEqualTo(ApprovalActionType.DELEGATED);
        assertThat(actionCaptor.getValue().actorMemberId()).isEqualTo(approverMemberId);
        assertThat(actionCaptor.getValue().delegateMemberId()).isEqualTo(delegateMemberId);
    }

    @Test
    void delegate_alreadyDelegated_throws409() {
        ApprovalRequest request = pendingRequest().assign(UUID.randomUUID());
        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> approvalService.delegate(approverUserId, approvalId, UUID.randomUUID()))
                .isInstanceOf(InvalidApprovalStateException.class)
                .hasFieldOrPropertyWithValue("code", "APPROVAL_ALREADY_DELEGATED");
    }

    @Test
    void delegate_toActor_throws403() {
        ApprovalRequest request = pendingRequest();
        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingApprovalOrder()));
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(approverMember()));
        when(memberRepository.findById(approverMemberId)).thenReturn(Optional.of(approverMember()));

        assertThatThrownBy(() -> approvalService.delegate(approverUserId, approvalId, approverMemberId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "DELEGATE_SELF");
    }

    @Test
    void delegate_toOrderPlacer_throws403() {
        ApprovalRequest request = pendingRequest();
        UUID delegateMemberId = UUID.randomUUID();
        CompanyMember placerMember = new CompanyMember(delegateMemberId, companyId, placerUserId,
                CompanyRole.PROCUREMENT_MANAGER, null, null);

        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingApprovalOrder()));
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(approverMember()));
        when(memberRepository.findById(delegateMemberId)).thenReturn(Optional.of(placerMember));

        assertThatThrownBy(() -> approvalService.delegate(approverUserId, approvalId, delegateMemberId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_APPROVAL_PROHIBITED");
    }

    @Test
    void delegate_memberOfAnotherCompany_throws404() {
        ApprovalRequest request = pendingRequest();
        UUID delegateMemberId = UUID.randomUUID();
        CompanyMember foreign = new CompanyMember(delegateMemberId, UUID.randomUUID(), UUID.randomUUID(),
                CompanyRole.PROCUREMENT_MANAGER, null, null);

        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingApprovalOrder()));
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(approverMember()));
        when(memberRepository.findById(delegateMemberId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> approvalService.delegate(approverUserId, approvalId, delegateMemberId))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "MEMBER_NOT_FOUND");
    }

    @Test
    void delegate_ineligibleDelegate_throws403() {
        ApprovalRequest request = pendingRequest();
        UUID delegateMemberId = UUID.randomUUID();
        CompanyMember delegate = new CompanyMember(delegateMemberId, companyId, UUID.randomUUID(),
                CompanyRole.VIEWER, null, null);

        when(approvalRequestRepository.findById(approvalId)).thenReturn(Optional.of(request));
        when(approvalRequestRepository.findByIdForUpdate(approvalId)).thenReturn(Optional.of(request));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingApprovalOrder()));
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(approverMember()));
        when(memberRepository.findById(delegateMemberId)).thenReturn(Optional.of(delegate));
        when(eligibilityResolver.eligibleApprovers(any(), any(), any(), any()))
                .thenReturn(List.of(approverMember())); // delegate absent

        assertThatThrownBy(() -> approvalService.delegate(approverUserId, approvalId, delegateMemberId))
                .isInstanceOf(ForbiddenException.class)
                .hasFieldOrPropertyWithValue("code", "DELEGATE_INELIGIBLE");
    }

    @Test
    void list_allSiteMember_seesCompanyWide() {
        CompanyMember member = approverMember();
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(member));
        when(siteAssignmentRepository.findSiteIdsByMemberId(member.id())).thenReturn(List.of());

        approvalService.list(approverUserId, companyId);

        verify(b2bAuthorizer).authorize(approverUserId, companyId, CompanyPermission.APPROVAL_VIEW, null, false);
        verify(approvalRequestRepository).findByCompanyVisibleInSites(companyId, null);
    }

    @Test
    void list_siteScopedMember_filteredToAssignedSites() {
        CompanyMember member = approverMember();
        UUID siteId = UUID.randomUUID();
        when(memberRepository.findByCompanyIdAndUserId(companyId, approverUserId))
                .thenReturn(Optional.of(member));
        when(siteAssignmentRepository.findSiteIdsByMemberId(member.id())).thenReturn(List.of(siteId));

        approvalService.list(approverUserId, companyId);

        verify(approvalRequestRepository).findByCompanyVisibleInSites(companyId, List.of(siteId));
    }
}
