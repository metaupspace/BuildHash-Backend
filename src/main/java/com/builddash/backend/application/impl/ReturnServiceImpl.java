package com.builddash.backend.application.impl;

import com.builddash.backend.api.dto.request.ReturnLineItemRequest;
import com.builddash.backend.application.event.ReturnStatusChangedEvent;
import com.builddash.backend.application.service.RefundService;
import com.builddash.backend.application.service.ReturnService;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.enums.ReturnReason;
import com.builddash.backend.domain.enums.ReturnStatus;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.InvalidOrderStateException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.ReturnAlreadyExistsException;
import com.builddash.backend.domain.model.Category;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.model.Refund;
import com.builddash.backend.domain.model.Return;
import com.builddash.backend.domain.model.ReturnLineItem;
import com.builddash.backend.domain.port.CategoryRepository;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.domain.service.ReturnRefundCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final OrderRepository orderRepository;
    private final ReturnRepository returnRepository;
    private final RefundRepository refundRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectStorage objectStorage;
    private final RefundService refundService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Override
    public Return createReturn(UUID userId, UUID orderId, ReturnReason reason, List<ReturnLineItemRequest> requestedItems, List<MultipartFile> photos) {
        if (photos == null || photos.isEmpty()) {
            throw new BadRequestException("PHOTOS_REQUIRED", "At least one return photo is required");
        }
        List<MultipartFile> validPhotos = photos.stream()
                .filter(p -> p != null && !p.isEmpty())
                .toList();
        if (validPhotos.isEmpty()) {
            throw new BadRequestException("PHOTOS_REQUIRED", "At least one return photo is required");
        }

        for (MultipartFile photo : validPhotos) {
            String contentType = photo.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.containsKey(contentType.toLowerCase())) {
                throw new BadRequestException("INVALID_PHOTO_TYPE", "Photo must be image/jpeg, image/png, or image/webp");
            }
        }

        try {
            return transactionTemplate.execute(status -> {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderId));

                if (!order.userId().equals(userId)) {
                    throw new NotFoundException("ORDER_NOT_FOUND", "Order not found: " + orderId);
                }

                if (order.status() != OrderStatus.DELIVERED) {
                    throw new InvalidOrderStateException(order.status().name(), "RETURN_REQUESTED");
                }

                // Client-retry guard: one ACTIVE return per order. No returned-quantity tracking
                // exists (PLAN_PHASE6 Section 1), so a second return would re-return the same items
                // and trigger a second refund. REJECTED is the one re-entry door — rejection is
                // terminal and pre-refund, so a corrected re-submission stays legitimate.
                returnRepository.findByOrderId(orderId)
                        .filter(existing -> existing.status() != ReturnStatus.REJECTED)
                        .ifPresent(existing -> {
                            throw new ReturnAlreadyExistsException(orderId);
                        });

                Map<UUID, OrderLineItem> orderItemMap = order.lineItems().stream()
                        .collect(Collectors.toMap(OrderLineItem::productId, Function.identity()));

                UUID returnId = UUID.randomUUID();
                List<ReturnLineItem> lineItems = new ArrayList<>();

                for (ReturnLineItemRequest reqItem : requestedItems) {
                    OrderLineItem orderItem = orderItemMap.get(reqItem.productId());
                    if (orderItem == null) {
                        throw new BadRequestException("INVALID_PRODUCT", "Product " + reqItem.productId() + " is not part of order " + orderId);
                    }
                    if (reqItem.quantity() > orderItem.quantity()) {
                        throw new BadRequestException("INVALID_QUANTITY", "Requested return quantity exceeds ordered quantity for product " + reqItem.productId());
                    }

                    Product product = productRepository.findById(reqItem.productId()).orElse(null);
                    UUID categoryId = product != null ? product.getCategoryId() : null;
                    int windowDays = 7;
                    if (categoryId != null) {
                        windowDays = categoryRepository.findById(categoryId)
                                .map(Category::getReturnWindowDays)
                                .filter(w -> w != null && w > 0)
                                .orElse(7);
                    }

                    Instant deliveredAt = order.placedAt();
                    if (deliveredAt != null && Instant.now().isAfter(deliveredAt.plus(Duration.ofDays(windowDays)))) {
                        throw new BadRequestException("RETURN_WINDOW_EXPIRED", "Return window of " + windowDays + " days has expired for product " + reqItem.productId());
                    }

                    BigDecimal refundAmount = ReturnRefundCalculator.calculateItemRefund(orderItem, reqItem.quantity());
                    lineItems.add(new ReturnLineItem(
                            UUID.randomUUID(),
                            returnId,
                            reqItem.productId(),
                            reqItem.quantity(),
                            refundAmount
                    ));
                }

                List<String> photoKeys = new ArrayList<>();
                for (MultipartFile photo : validPhotos) {
                    String contentType = photo.getContentType().toLowerCase();
                    String ext = ALLOWED_IMAGE_TYPES.get(contentType);
                    String storageKey = "returns/" + returnId + "/photos/" + UUID.randomUUID() + "." + ext;
                    try {
                        objectStorage.store(storageKey, photo.getBytes(), contentType);
                        photoKeys.add(storageKey);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to read photo bytes for upload", e);
                    }
                }

                Return returnObj = new Return(
                        returnId,
                        orderId,
                        userId,
                        ReturnStatus.REQUESTED,
                        reason,
                        photoKeys,
                        lineItems,
                        Instant.now(),
                        Instant.now()
                );

                Return saved = returnRepository.save(returnObj);
                log.info("Created return {} for order {} with {} items", saved.id(), orderId, lineItems.size());
                return saved;
            });
        } catch (DataIntegrityViolationException e) {
            // uq_returns_one_active_per_order (V24): the concurrent race loser lands here
            // — caught at the template boundary where commit-time flush violations surface,
            // translated to the same contract the sequential guard above enforces.
            throw new ReturnAlreadyExistsException(orderId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Return getReturn(UUID userId, List<String> roles, UUID returnId) {
        Return returnObj = returnRepository.findById(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));

        boolean isPrivileged = isPrivileged(roles);
        if (!isPrivileged && !returnObj.userId().equals(userId)) {
            throw new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId);
        }

        return returnObj;
    }

    /**
     * H0.2: reject/QC (and the refund initiation QC delegates to) are VENDOR/ADMIN
     * operations. Non-privileged principals get the same existence-hiding 404 the
     * read path uses, so arbitrary return ids cannot be probed.
     */
    private static boolean isPrivileged(List<String> roles) {
        return roles != null && (roles.contains("ADMIN") || roles.contains("VENDOR"));
    }

    private static void requirePrivileged(List<String> roles, UUID returnId) {
        if (!isPrivileged(roles)) {
            throw new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Refund> getRefund(UUID returnId) {
        return refundRepository.findByReturnId(returnId);
    }

    @Override
    @Transactional
    public Return approve(UUID returnId) {
        Return returnObj = returnRepository.findById(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));
        Return approved = returnObj.approve();
        Return saved = returnRepository.save(approved);
        eventPublisher.publishEvent(new ReturnStatusChangedEvent(returnId, returnObj.status(), ReturnStatus.APPROVED));
        return saved;
    }

    @Override
    @Transactional
    public Return schedulePickup(UUID returnId) {
        Return returnObj = returnRepository.findById(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));
        Return scheduled = returnObj.schedulePickup();
        Return saved = returnRepository.save(scheduled);
        eventPublisher.publishEvent(new ReturnStatusChangedEvent(returnId, returnObj.status(), ReturnStatus.PICKUP_SCHEDULED));
        return saved;
    }

    @Override
    @Transactional
    public Return pickUp(UUID returnId) {
        Return returnObj = returnRepository.findById(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));
        Return pickedUp = returnObj.pickUp();
        Return saved = returnRepository.save(pickedUp);
        eventPublisher.publishEvent(new ReturnStatusChangedEvent(returnId, returnObj.status(), ReturnStatus.PICKED_UP));
        return saved;
    }

    @Override
    public Return passQc(UUID returnId, UUID userId, List<String> roles) {
        // H0.2: the filter chain gates the HTTP route, the service is the authority —
        // internal callers cannot reach the refund trigger without VENDOR/ADMIN.
        requirePrivileged(roles, returnId);
        // 8.1-C boundary: the QC transition and its event commit BEFORE refund initiation
        // is delegated — the gateway workflow must never run inside this method's tx.
        transactionTemplate.executeWithoutResult(status -> {
            Return returnObj = returnRepository.findById(returnId)
                    .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));

            Return inQc = returnObj.passQc();
            returnRepository.save(inQc);
            eventPublisher.publishEvent(new ReturnStatusChangedEvent(returnId, returnObj.status(), ReturnStatus.QC));
        });

        refundService.initiateRefund(returnId);

        return returnRepository.findById(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));
    }

    @Override
    @Transactional
    public Return reject(UUID returnId, UUID userId, List<String> roles) {
        requirePrivileged(roles, returnId);
        Return returnObj = returnRepository.findById(returnId)
                .orElseThrow(() -> new NotFoundException("RETURN_NOT_FOUND", "Return not found: " + returnId));
        Return rejected = returnObj.reject();
        Return saved = returnRepository.save(rejected);
        eventPublisher.publishEvent(new ReturnStatusChangedEvent(returnId, returnObj.status(), ReturnStatus.REJECTED));
        return saved;
    }
}
