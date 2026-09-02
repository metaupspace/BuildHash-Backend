package com.builddash.backend.application.impl;

import com.builddash.backend.api.dto.request.ReturnLineItemRequest;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.RefundService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.ReturnAlreadyExistsException;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.DeliveryTrackingEvent;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.DeliveryTrackingEventRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for ReturnServiceImpl (H3): lifecycle reachability, window anchor,
 * repository cardinality, row-locking, and B2B authorization scoping.
 */
@ExtendWith(MockitoExtension.class)
class ReturnServiceImplTest {

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
    private DeliveryTrackingEventRepository trackingEventRepository;
    @Mock
    private ObjectStorage objectStorage;
    @Mock
    private RefundService refundService;
    @Mock
    private B2bAuthorizer b2bAuthorizer;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ReturnServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new ReturnServiceImpl(orderRepository, returnRepository, refundRepository,
                productRepository, categoryRepository, trackingEventRepository, objectStorage,
                refundService, b2bAuthorizer, eventPublisher, transactionTemplate);
    }

    private Order deliveredOrder() {
        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 2,
                new BigDecimal("350.00"), new BigDecimal("294.00"), new BigDecimal("1344.00"));
        return new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("1344.00"), OrderStatus.DELIVERED, UUID.randomUUID(), Instant.now(),
                null, null, List.of(item));
    }

    private List<MultipartFile> photos() {
        return List.of(new MockMultipartFile("photos", "damage.jpg", "image/jpeg", new byte[]{1, 2, 3}));
    }

    private void stubHappyPathDependencies() {
        lenient().when(orderRepository.findById(orderId)).thenReturn(Optional.of(deliveredOrder()));
        Product product = org.mockito.Mockito.mock(Product.class);
        lenient().when(product.getCategoryId()).thenReturn(categoryId);
        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        Category category = org.mockito.Mockito.mock(Category.class);
        lenient().when(category.getReturnWindowDays()).thenReturn(7);
        lenient().when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    }

    @Test
    void createReturn_dataIntegrityViolationFromUniqueIndex_translatedToReturnAlreadyExists() {
        stubHappyPathDependencies();
        lenient().when(returnRepository.findActiveByOrderId(orderId)).thenReturn(Optional.empty());
        lenient().when(objectStorage.store(anyString(), any(), anyString())).thenReturn("key");
        when(returnRepository.save(any(Return.class)))
                .thenThrow(new DataIntegrityViolationException("uq_returns_one_active_per_order"));

        assertThatThrownBy(() -> service.createReturn(userId, orderId, ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1)), photos()))
                .isInstanceOf(ReturnAlreadyExistsException.class);
    }

    @Test
    void createReturn_existingRejectedReturn_allowsResubmission() {
        stubHappyPathDependencies();
        when(returnRepository.findActiveByOrderId(orderId)).thenReturn(Optional.empty());
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        Return created = service.createReturn(userId, orderId, ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1)), photos());

        assertThat(created.orderId()).isEqualTo(orderId);
        assertThat(created.status()).isEqualTo(ReturnStatus.REQUESTED);
    }

    @Test
    void createReturn_existingActiveReturn_guardThrowsBeforeInsert() {
        stubHappyPathDependencies();
        Return active = new Return(UUID.randomUUID(), orderId, userId, ReturnStatus.QC,
                ReturnReason.DAMAGED, List.of(), List.of(), Instant.now(), Instant.now());
        when(returnRepository.findActiveByOrderId(orderId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.createReturn(userId, orderId, ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1)), photos()))
                .isInstanceOf(ReturnAlreadyExistsException.class);

        verify(returnRepository, never()).save(any());
    }

    @Test
    void createReturn_b2bOrder_authorizesAgainstB2bAuthorizer() {
        UUID companyId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 2,
                new BigDecimal("350.00"), new BigDecimal("294.00"), new BigDecimal("1344.00"));
        Order b2bOrder = new Order(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("1344.00"), OrderStatus.DELIVERED, UUID.randomUUID(), Instant.now(),
                null, null, List.of(item), companyId, siteId, Instant.now());

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(b2bOrder));
        Product product = org.mockito.Mockito.mock(Product.class);
        lenient().when(product.getCategoryId()).thenReturn(categoryId);
        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        Category category = org.mockito.Mockito.mock(Category.class);
        lenient().when(category.getReturnWindowDays()).thenReturn(7);
        lenient().when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(returnRepository.findActiveByOrderId(orderId)).thenReturn(Optional.empty());
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        Return created = service.createReturn(userId, orderId, ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1)), photos());

        assertThat(created.orderId()).isEqualTo(orderId);
        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.ORDER_CREATE, siteId, true);
    }

    @Test
    void createReturn_windowAnchoredToDeliveredTrackingEvent_succeedsEvenIfPlacedLongAgo() {
        // Order placed 20 days ago, but DELIVERED 2 days ago. Category window = 7 days.
        Instant placedAt = Instant.now().minus(Duration.ofDays(20));
        Instant deliveredAt = Instant.now().minus(Duration.ofDays(2));

        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 2,
                new BigDecimal("350.00"), new BigDecimal("294.00"), new BigDecimal("1344.00"));
        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("1344.00"), OrderStatus.DELIVERED, UUID.randomUUID(), placedAt,
                null, null, List.of(item));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        Product product = org.mockito.Mockito.mock(Product.class);
        lenient().when(product.getCategoryId()).thenReturn(categoryId);
        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        Category category = org.mockito.Mockito.mock(Category.class);
        lenient().when(category.getReturnWindowDays()).thenReturn(7);
        lenient().when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(trackingEventRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(
                new DeliveryTrackingEvent(UUID.randomUUID(), orderId, OrderStatus.DELIVERED, 18.0, 72.0, deliveredAt)));
        when(returnRepository.findActiveByOrderId(orderId)).thenReturn(Optional.empty());
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        Return created = service.createReturn(userId, orderId, ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1)), photos());

        assertThat(created.status()).isEqualTo(ReturnStatus.REQUESTED);
    }

    @Test
    void createReturn_windowExpiredFromDelivery_throwsBadRequest() {
        Instant placedAt = Instant.now().minus(Duration.ofDays(20));
        Instant deliveredAt = Instant.now().minus(Duration.ofDays(10)); // 10 days ago > 7 days window

        OrderLineItem item = new OrderLineItem(UUID.randomUUID(), productId, 2,
                new BigDecimal("350.00"), new BigDecimal("294.00"), new BigDecimal("1344.00"));
        Order order = new Order(orderId, userId, UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(),
                new BigDecimal("1344.00"), OrderStatus.DELIVERED, UUID.randomUUID(), placedAt,
                null, null, List.of(item));

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        Product product = org.mockito.Mockito.mock(Product.class);
        lenient().when(product.getCategoryId()).thenReturn(categoryId);
        lenient().when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        Category category = org.mockito.Mockito.mock(Category.class);
        lenient().when(category.getReturnWindowDays()).thenReturn(7);
        lenient().when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(trackingEventRepository.findLatestByOrderId(orderId)).thenReturn(Optional.of(
                new DeliveryTrackingEvent(UUID.randomUUID(), orderId, OrderStatus.DELIVERED, 18.0, 72.0, deliveredAt)));
        when(returnRepository.findActiveByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createReturn(userId, orderId, ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1)), photos()))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("code", "RETURN_WINDOW_EXPIRED");
    }

    @Test
    void passQc_transitionsAndSaves_thenDelegatesRefundInitiation() {
        Return pickedUp = new Return(UUID.randomUUID(), orderId, userId, ReturnStatus.PICKED_UP,
                ReturnReason.DAMAGED, List.of(), List.of(), Instant.now(), Instant.now());
        lenient().when(returnRepository.findByIdForUpdate(pickedUp.id())).thenReturn(Optional.of(pickedUp));
        lenient().when(returnRepository.findById(pickedUp.id())).thenReturn(Optional.of(pickedUp));

        service.passQc(pickedUp.id(), userId, List.of("VENDOR"));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ReturnStatusChangedEvent event = (ReturnStatusChangedEvent) eventCaptor.getValue();
        assertThat(event.to()).isEqualTo(ReturnStatus.QC);
        verify(returnRepository).save(any(Return.class));
        verify(refundService).initiateRefund(pickedUp.id());
    }

    @Test
    void approve_nonPrivileged_throwsNotFound() {
        UUID returnId = UUID.randomUUID();
        assertThatThrownBy(() -> service.approve(returnId, userId, List.of("USER")))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RETURN_NOT_FOUND");
    }

    @Test
    void schedulePickup_nonPrivileged_throwsNotFound() {
        UUID returnId = UUID.randomUUID();
        assertThatThrownBy(() -> service.schedulePickup(returnId, userId, List.of("USER")))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RETURN_NOT_FOUND");
    }

    @Test
    void pickUp_nonPrivileged_throwsNotFound() {
        UUID returnId = UUID.randomUUID();
        assertThatThrownBy(() -> service.pickUp(returnId, userId, List.of("USER")))
                .isInstanceOf(NotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "RETURN_NOT_FOUND");
    }

    @Test
    void approve_privileged_locksAndTransitions() {
        Return requested = new Return(UUID.randomUUID(), orderId, userId, ReturnStatus.REQUESTED,
                ReturnReason.DAMAGED, List.of(), List.of(), Instant.now(), Instant.now());
        when(returnRepository.findByIdForUpdate(requested.id())).thenReturn(Optional.of(requested));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        Return approved = service.approve(requested.id(), userId, List.of("VENDOR"));

        assertThat(approved.status()).isEqualTo(ReturnStatus.APPROVED);
        verify(returnRepository).findByIdForUpdate(requested.id());
    }

    @Test
    void schedulePickup_privileged_locksAndTransitions() {
        Return approved = new Return(UUID.randomUUID(), orderId, userId, ReturnStatus.APPROVED,
                ReturnReason.DAMAGED, List.of(), List.of(), Instant.now(), Instant.now());
        when(returnRepository.findByIdForUpdate(approved.id())).thenReturn(Optional.of(approved));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        Return scheduled = service.schedulePickup(approved.id(), userId, List.of("ADMIN"));

        assertThat(scheduled.status()).isEqualTo(ReturnStatus.PICKUP_SCHEDULED);
        verify(returnRepository).findByIdForUpdate(approved.id());
    }

    @Test
    void pickUp_privileged_locksAndTransitions() {
        Return scheduled = new Return(UUID.randomUUID(), orderId, userId, ReturnStatus.PICKUP_SCHEDULED,
                ReturnReason.DAMAGED, List.of(), List.of(), Instant.now(), Instant.now());
        when(returnRepository.findByIdForUpdate(scheduled.id())).thenReturn(Optional.of(scheduled));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        Return pickedUp = service.pickUp(scheduled.id(), userId, List.of("VENDOR"));

        assertThat(pickedUp.status()).isEqualTo(ReturnStatus.PICKED_UP);
        verify(returnRepository).findByIdForUpdate(scheduled.id());
    }

    @Test
    void reject_fromPickedUp_privileged_locksAndTransitions() {
        Return pickedUp = new Return(UUID.randomUUID(), orderId, userId, ReturnStatus.PICKED_UP,
                ReturnReason.DAMAGED, List.of(), List.of(), Instant.now(), Instant.now());
        when(returnRepository.findByIdForUpdate(pickedUp.id())).thenReturn(Optional.of(pickedUp));
        when(returnRepository.save(any(Return.class))).thenAnswer(inv -> inv.getArgument(0));

        Return rejected = service.reject(pickedUp.id(), userId, List.of("VENDOR"));

        assertThat(rejected.status()).isEqualTo(ReturnStatus.REJECTED);
        verify(returnRepository).findByIdForUpdate(pickedUp.id());
    }
}
