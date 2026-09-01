package com.builddash.backend.application.impl;

import com.builddash.backend.application.event.ApprovalEscalatedEvent;
import com.builddash.backend.application.service.ApprovalEligibilityResolver;
import com.builddash.backend.domain.enums.ApprovalActionType;
import com.builddash.backend.domain.enums.ApprovalRequestStatus;
import com.builddash.backend.domain.enums.CompanyRole;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.model.ApprovalAction;
import com.builddash.backend.domain.model.ApprovalRequest;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.port.ApprovalActionRepository;
import com.builddash.backend.domain.port.ApprovalRequestRepository;
import com.builddash.backend.domain.port.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalEscalationServiceImplTest {

    @Mock
    private ApprovalRequestRepository requestRepository;
    @Mock
    private ApprovalActionRepository actionRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ApprovalEligibilityResolver eligibilityResolver;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ApprovalEscalationServiceImpl escalationService;

    private final UUID requestId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID placerUserId = UUID.randomUUID();

    @BeforeEach
    void wireSelfProxy() throws Exception {
        // Production wires the @Lazy self-proxy for REQUIRES_NEW; the unit test points it
        // at the same instance — escalateOne's logic is what's under test here.
        Field self = ApprovalEscalationServiceImpl.class.getDeclaredField("self");
        self.setAccessible(true);
        self.set(escalationService, escalationService);
    }

    private ApprovalRequest dueRequest(int stageIndex) {
        return new ApprovalRequest(requestId, orderId, companyId, ApprovalRequestStatus.PENDING,
                stageIndex, CompanyRole.PROCUREMENT_MANAGER, UUID.randomUUID(),
                Instant.now().minus(Duration.ofMinutes(1)), new BigDecimal("150.00"),
                List.of(), null, List.of(), null,
                List.of(CompanyRole.PROCUREMENT_MANAGER, CompanyRole.SITE_SUPERVISOR, CompanyRole.OWNER),
                24, 1, null, null);
    }

    private Order gatedOrder() {
        return new Order(orderId, placerUserId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("150.00"), OrderStatus.PENDING_APPROVAL, null, Instant.now(),
                null, null, List.of(), companyId, null, null);
    }

    @Test
    void escalate_due_advancesToFirstLaterStageWithEligibleMember() {
        ApprovalRequest request = dueRequest(0);
        when(requestRepository.findDueIds(any())).thenReturn(List.of(requestId));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(gatedOrder()));
        when(eligibilityResolver.hasEligibleApprover(eq(companyId), eq(CompanyRole.SITE_SUPERVISOR),
                any(), eq(placerUserId))).thenReturn(true);

        int processed = escalationService.escalateDue();

        assertThat(processed).isEqualTo(1);
        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).save(captor.capture());
        ApprovalRequest escalated = captor.getValue();
        assertThat(escalated.currentStageIndex()).isEqualTo(1);
        assertThat(escalated.currentRole()).isEqualTo(CompanyRole.SITE_SUPERVISOR);
        assertThat(escalated.assignedMemberId()).isNull(); // delegation does not survive escalation
        assertThat(escalated.status()).isEqualTo(ApprovalRequestStatus.PENDING); // never auto-decided
        assertThat(escalated.escalationDueAt()).isAfter(Instant.now());

        ArgumentCaptor<ApprovalAction> action = ArgumentCaptor.forClass(ApprovalAction.class);
        verify(actionRepository).save(action.capture());
        assertThat(action.getValue().type()).isEqualTo(ApprovalActionType.ESCALATED);
        assertThat(action.getValue().stageIndex()).isEqualTo(1);

        verify(eventPublisher).publishEvent(any(ApprovalEscalatedEvent.class));
    }

    @Test
    void escalate_skipsStagesWithoutEligibleMembers() {
        ApprovalRequest request = dueRequest(0);
        when(requestRepository.findDueIds(any())).thenReturn(List.of(requestId));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(gatedOrder()));
        when(eligibilityResolver.hasEligibleApprover(eq(companyId), eq(CompanyRole.SITE_SUPERVISOR),
                any(), eq(placerUserId))).thenReturn(false);
        when(eligibilityResolver.hasEligibleApprover(eq(companyId), eq(CompanyRole.OWNER),
                any(), eq(placerUserId))).thenReturn(true);

        escalationService.escalateDue();

        ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).save(captor.capture());
        assertThat(captor.getValue().currentStageIndex()).isEqualTo(2); // skipped empty stage 1
        assertThat(captor.getValue().currentRole()).isEqualTo(CompanyRole.OWNER);
    }

    @Test
    void escalate_noEligibleLaterStage_blocksOnceAndClearsDueAt() {
        ApprovalRequest request = dueRequest(1);
        when(requestRepository.findDueIds(any())).thenReturn(List.of(requestId));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(gatedOrder()));
        when(eligibilityResolver.hasEligibleApprover(any(), any(), any(), any())).thenReturn(false);
        when(actionRepository.existsByRequestIdAndTypeAndStageIndex(requestId,
                ApprovalActionType.ESCALATION_BLOCKED, 1)).thenReturn(false);

        escalationService.escalateDue();

        ArgumentCaptor<ApprovalRequest> requestCaptor = ArgumentCaptor.forClass(ApprovalRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().status()).isEqualTo(ApprovalRequestStatus.PENDING); // never cancelled
        assertThat(requestCaptor.getValue().escalationDueAt()).isNull();      // stops future sweeps
        assertThat(requestCaptor.getValue().currentStageIndex()).isEqualTo(1); // stage unchanged

        ArgumentCaptor<ApprovalAction> action = ArgumentCaptor.forClass(ApprovalAction.class);
        verify(actionRepository).save(action.capture());
        assertThat(action.getValue().type()).isEqualTo(ApprovalActionType.ESCALATION_BLOCKED);
        assertThat(action.getValue().stageIndex()).isEqualTo(1);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void escalate_secondInstanceRacing_recheckSkipsMutation() {
        ApprovalRequest request = dueRequest(0);
        // Another instance already advanced it: locked re-read shows a future dueAt.
        ApprovalRequest advanced = request.escalateTo(1, Instant.now().plus(Duration.ofHours(24)));
        when(requestRepository.findDueIds(any())).thenReturn(List.of(requestId));
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(advanced));

        int processed = escalationService.escalateDue();

        assertThat(processed).isZero();
        verify(requestRepository, never()).save(any());
        verify(actionRepository, never()).save(any());
    }

    @Test
    void escalate_terminalRequestSkipped_noAutoDecision() {
        when(requestRepository.findDueIds(any())).thenReturn(List.of(requestId));
        ApprovalRequest decided = dueRequest(0).approve();
        when(requestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(decided));

        assertThat(escalationService.escalateDue()).isZero();
        verify(requestRepository, never()).save(any());
        verify(actionRepository, never()).save(any());
    }

    @Test
    void escalate_oneFailingRequestDoesNotKillTheBatch() {
        ApprovalRequest request = dueRequest(0);
        when(requestRepository.findDueIds(any())).thenReturn(List.of(requestId, UUID.randomUUID()));
        when(requestRepository.findByIdForUpdate(requestId))
                .thenReturn(Optional.of(request))
                .thenThrow(new RuntimeException("row vanished"));
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(gatedOrder()));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eligibilityResolver.hasEligibleApprover(any(), any(), any(), any())).thenReturn(true);

        int processed = escalationService.escalateDue();

        assertThat(processed).isEqualTo(1); // first succeeded, second failed, no propagation
    }
}
