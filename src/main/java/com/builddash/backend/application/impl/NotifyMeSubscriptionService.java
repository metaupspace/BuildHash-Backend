package com.builddash.backend.application.impl;

import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.NotifyMeSubscription;
import com.builddash.backend.domain.port.NotifyMeSubscriptionRepository;
import com.builddash.backend.domain.port.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * No separate interface — single orchestration workflow, single caller (NotifyMeController),
 * same judgment call as OtpSendService. The "fire notifications on restock" job is out of
 * scope for Wave 2 — no inventory-update event exists yet (Product.stock is a static stub,
 * see PLAN_PHASE1.md Open Question #1). This service only records the subscription.
 */
@RequiredArgsConstructor
@Service
public class NotifyMeSubscriptionService {

    private final NotifyMeSubscriptionRepository notifyMeSubscriptionRepository;
    private final ProductRepository productRepository;


    @Transactional
    public NotifyMeSubscription subscribe(UUID productId, UUID userId) {
        return notifyMeSubscriptionRepository.findByProductIdAndUserId(productId, userId)
                .orElseGet(() -> {
                    productRepository.findById(productId)
                            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + productId));
                    NotifyMeSubscription subscription = new NotifyMeSubscription();
                    subscription.setProductId(productId);
                    subscription.setUserId(userId);
                    return notifyMeSubscriptionRepository.save(subscription);
                });
    }
}
