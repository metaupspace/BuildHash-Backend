package com.builddash.backend.application.impl;

import com.builddash.backend.api.dto.request.ReturnLineItemRequest;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.ReturnAlreadyExistsException;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.application.service.RefundService;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
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
 * Unit coverage for the V24 partial-unique backstop (8.1-A): the concurrent race loser's
 * DataIntegrityViolationException is translated into the same ReturnAlreadyExistsException
 * the sequential guard throws, and REJECTED resubmission stays legal.
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
    private ObjectStorage objectStorage;
    @Mock
    private RefundService refundService;
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
        service = new ReturnServiceImpl(orderRepository, returnRepository, refundRepository,
                productRepository, categoryRepository, objectStorage, refundService, eventPublisher,
                transactionTemplate);
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
        lenient().when(returnRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        lenient().when(objectStorage.store(anyString(), any(), anyString())).thenReturn("key");
        // The V24 partial unique fires at the insert/commit boundary — any layer the
        // exception surfaces from, it must land as the existing 409 contract.
        when(returnRepository.save(any(Return.class)))
                .thenThrow(new DataIntegrityViolationException("uq_returns_one_active_per_order"));

        assertThatThrownBy(() -> service.createReturn(userId, orderId, ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1)), photos()))
                .isInstanceOf(ReturnAlreadyExistsException.class);
    }

    @Test
    void createReturn_existingRejectedReturn_allowsResubmission() {
        stubHappyPathDependencies();
        Return rejected = new Return(UUID.randomUUID(), orderId, userId, ReturnStatus.REJECTED,
                ReturnReason.DAMAGED, List.of(), List.of(), Instant.now(), Instant.now());
        when(returnRepository.findByOrderId(orderId)).thenReturn(Optional.of(rejected));
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
        when(returnRepository.findByOrderId(orderId)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.createReturn(userId, orderId, ReturnReason.DAMAGED,
                List.of(new ReturnLineItemRequest(productId, 1)), photos()))
                .isInstanceOf(ReturnAlreadyExistsException.class);

        verify(returnRepository, never()).save(any());
    }
}
