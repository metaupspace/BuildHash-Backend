package com.builddash.backend.application.impl;

import com.builddash.backend.api.dto.request.ReturnLineItemRequest;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.RefundService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 7 Checkpoint A event-publish proofs for ReturnServiceImpl: each state method fires exactly
 * one ReturnStatusChangedEvent with the correct from/to (passQc is PICKED_UP→QC only — the
 * REFUND_INITIATED leg belongs to RefundServiceImpl), and createReturn publishes nothing
 * (REQUESTED is the customer's own action).
 */
@ExtendWith(MockitoExtension.class)
class ReturnServiceEventPublishTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ReturnRepository returnRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ObjectStorage objectStorage;

    @Mock
    private RefundService refundService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReturnServiceImpl(orderRepository, returnRepository, refundRepository,
                productRepository, categoryRepository, objectStorage, refundService, eventPublisher);
    }

    private Return returnIn(ReturnStatus status) {
        return new Return(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status,
                ReturnReason.DAMAGED, List.of("photo.jpg"), List.of(), Instant.now(), Instant.now());
    }

    private ReturnStatusChangedEvent captureSingleEvent() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        return (ReturnStatusChangedEvent) captor.getValue();
    }

    private void stubFind(Return returnObj) {
        lenient().when(returnRepository.findById(returnObj.id())).thenReturn(Optional.of(returnObj));
        lenient().when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approve_firesRequestedToApproved() {
        Return returnObj = returnIn(ReturnStatus.REQUESTED);
        stubFind(returnObj);

        service.approve(returnObj.id());

        ReturnStatusChangedEvent event = captureSingleEvent();
        assertThat(event.returnId()).isEqualTo(returnObj.id());
        assertThat(event.from()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(event.to()).isEqualTo(ReturnStatus.APPROVED);
    }

    @Test
    void schedulePickup_firesApprovedToPickupScheduled() {
        Return returnObj = returnIn(ReturnStatus.APPROVED);
        stubFind(returnObj);

        service.schedulePickup(returnObj.id());

        ReturnStatusChangedEvent event = captureSingleEvent();
        assertThat(event.returnId()).isEqualTo(returnObj.id());
        assertThat(event.from()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(event.to()).isEqualTo(ReturnStatus.PICKUP_SCHEDULED);
    }

    @Test
    void pickUp_firesPickupScheduledToPickedUp() {
        Return returnObj = returnIn(ReturnStatus.PICKUP_SCHEDULED);
        stubFind(returnObj);

        service.pickUp(returnObj.id());

        ReturnStatusChangedEvent event = captureSingleEvent();
        assertThat(event.returnId()).isEqualTo(returnObj.id());
        assertThat(event.from()).isEqualTo(ReturnStatus.PICKUP_SCHEDULED);
        assertThat(event.to()).isEqualTo(ReturnStatus.PICKED_UP);
    }

    @Test
    void passQc_firesPickedUpToQcOnly() {
        Return returnObj = returnIn(ReturnStatus.PICKED_UP);
        stubFind(returnObj);

        service.passQc(returnObj.id());

        ReturnStatusChangedEvent event = captureSingleEvent();
        assertThat(event.returnId()).isEqualTo(returnObj.id());
        assertThat(event.from()).isEqualTo(ReturnStatus.PICKED_UP);
        assertThat(event.to()).isEqualTo(ReturnStatus.QC);
    }

    @Test
    void reject_fromRequested_firesRequestedToRejected() {
        Return returnObj = returnIn(ReturnStatus.REQUESTED);
        stubFind(returnObj);

        service.reject(returnObj.id());

        ReturnStatusChangedEvent event = captureSingleEvent();
        assertThat(event.returnId()).isEqualTo(returnObj.id());
        assertThat(event.from()).isEqualTo(ReturnStatus.REQUESTED);
        assertThat(event.to()).isEqualTo(ReturnStatus.REJECTED);
    }

    @Test
    void reject_fromApproved_firesApprovedToRejected() {
        Return returnObj = returnIn(ReturnStatus.APPROVED);
        stubFind(returnObj);

        service.reject(returnObj.id());

        ReturnStatusChangedEvent event = captureSingleEvent();
        assertThat(event.returnId()).isEqualTo(returnObj.id());
        assertThat(event.from()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(event.to()).isEqualTo(ReturnStatus.REJECTED);
    }

    @Test
    void createReturn_publishesNothing() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        OrderLineItem lineItem = new OrderLineItem(UUID.randomUUID(), productId, 5,
                new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("590.00"));
        Order order = new Order(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID(), null,
                BigDecimal.TEN, OrderStatus.DELIVERED, UUID.randomUUID(), Instant.now(), null, null,
                List.of(lineItem));
        when(orderRepository.findById(order.id())).thenReturn(Optional.of(order));
        lenient().when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        MultipartFile photo = mock(MultipartFile.class);
        lenient().when(photo.isEmpty()).thenReturn(false);
        lenient().when(photo.getContentType()).thenReturn("image/jpeg");
        lenient().when(photo.getBytes()).thenReturn(new byte[]{1});

        service.createReturn(userId, order.id(), ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 2)), List.of(photo));

        verify(eventPublisher, never()).publishEvent(any());
    }
}
