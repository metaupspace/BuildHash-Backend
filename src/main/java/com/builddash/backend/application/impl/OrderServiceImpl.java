package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.ApprovalGateService;
import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.CheckoutIntentService;
import com.builddash.backend.application.service.OrderService;
import com.builddash.backend.application.service.OrderResult;
import com.builddash.backend.application.service.ReorderResult;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.OrderStatus;
import com.builddash.backend.domain.exception.BadRequestException;
import com.builddash.backend.domain.exception.PaymentGatewayException;
import com.builddash.backend.domain.model.CheckoutIntent;
import com.builddash.backend.domain.model.CompanySite;
import com.builddash.backend.domain.model.Order;
import com.builddash.backend.domain.model.OrderLineItem;
import com.builddash.backend.domain.model.Payment;
import com.builddash.backend.domain.model.PaymentReference;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.port.CompanySiteRepository;
import com.builddash.backend.domain.port.IdempotencyKeyRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentGateway;
import com.builddash.backend.infra.config.OrderIdempotencyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.domain.model.CartLineItem;
import java.util.stream.Collectors;

import com.builddash.backend.domain.exception.InvalidOrderStateException;
import com.builddash.backend.domain.exception.PaymentRetryInProgressException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final CheckoutIntentService checkoutIntentService;
    private final OrderRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderIdempotencyProperties idempotencyProperties;
    private final com.builddash.backend.domain.port.PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final TransactionTemplate transactionTemplate;
    private final CartService cartService;
    private final com.builddash.backend.domain.port.CouponRepository couponRepository;
    private final com.builddash.backend.domain.port.CouponRedemptionRepository couponRedemptionRepository;
    private final B2bAuthorizer b2bAuthorizer;
    private final ApprovalGateService approvalGateService;
    private final CompanySiteRepository companySiteRepository;

    @Override
    public OrderResult create(UUID userId, UUID addressId, UUID slotId, LocalDate slotDate, BigDecimal expectedTotal,
                               UUID cartId, UUID siteId, String idempotencyKey) {
        // Draft carts are keyed by their project/source id — needed AFTER the transaction
        // to clear the right cart (the primary-cart call would leave a draft intact).
        UUID[] draftProjectId = new UUID[1];
        // H1.3: set only on the fresh-order, non-gated branch, in the SAME transaction as
        // the order itself. Null after the transaction means either PENDING_APPROVAL
        // (handled below before this is even read) or an idempotent retry that resolved
        // to an already-existing order — never "no claim was needed".
        Payment[] pendingClaim = new Payment[1];
        // H2.1: true only once a fresh B2B draft checkout actually claims its cart in
        // this call — never set on the idempotent-retry early-return paths.
        boolean[] isB2bDraftCheckout = new boolean[1];
        Order savedOrder;
        try {
            savedOrder = transactionTemplate.execute(status -> {
                // Rolling window (PLAN_PHASE8 decision 10): an expired key reads as absent —
                // the caller gets a genuinely new order, not the stale one.
                Instant idempotencyCutoff = Instant.now()
                        .minus(Duration.ofHours(idempotencyProperties.getIdempotencyWindowHours()));
                Optional<UUID> existingOrderId = idempotencyKeyRepository.findOrderId(idempotencyKey, idempotencyCutoff);
                if (existingOrderId.isPresent()) {
                    return existingOrderForKey(idempotencyKey, userId);
                }

                // B2B branch (9-D): a draft cart carries the company scope. Authorization runs
                // BEFORE createIntent so the COMPANY row precedes the delivery counter in the
                // global lock order — authorizing after the counter would invert it.
                PricedCart preRead = cartId != null ? cartService.getCartById(userId, cartId) : null;
                UUID b2bCompanyId = preRead != null ? preRead.companyId() : null;
                draftProjectId[0] = preRead != null ? preRead.projectId() : null;

                if (cartId != null && b2bCompanyId != null) {
                    // H2.1: atomic one-time consumption claim IS the concurrency guard — a
                    // second concurrent checkout against the same draft loses here, before
                    // any site/approval/gateway work, and a checkout that fails later in
                    // this same transaction rolls the claim back with everything else.
                    isB2bDraftCheckout[0] = true;
                    if (!cartService.claimForCheckout(cartId)) {
                        throw new BadRequestException("CART_ALREADY_CONSUMED",
                                "This draft cart has already been checked out: " + cartId);
                    }
                }

                UUID b2bSiteId = null;
                if (b2bCompanyId != null) {
                    // H0.5: site context is mandatory for company checkout — an optional
                    // siteId defeats site scoping and lets a site-only approval policy
                    // pass unmatched. B2C (no company) never enters this branch.
                    if (siteId == null) {
                        throw new BadRequestException("SITE_REQUIRED",
                                "siteId is required for company checkout");
                    }
                    b2bAuthorizer.authorize(userId, b2bCompanyId, CompanyPermission.ORDER_CREATE, siteId, true);
                    CompanySite site = companySiteRepository.findById(siteId)
                            .orElseThrow(() -> new BadRequestException("SITE_INVALID",
                                    "Unknown delivery site: " + siteId));
                    if (!site.companyId().equals(b2bCompanyId)) {
                        throw new BadRequestException("SITE_INVALID",
                                "Site " + siteId + " does not belong to the order's company");
                    }
                    if (!site.active()) {
                        throw new BadRequestException("SITE_INACTIVE",
                                "Site " + siteId + " is inactive");
                    }
                    b2bSiteId = siteId;
                }

                CheckoutIntent intent = checkoutIntentService.createIntent(userId, addressId, slotId, slotDate, expectedTotal, cartId);

                List<OrderLineItem> lineItems = intent.pricedCart().items().stream()
                        .map(item -> new OrderLineItem(
                                UUID.randomUUID(),
                                item.productId(),
                                item.quantity(),
                                item.unitFinalPrice(),
                                item.lineGst(),
                                item.lineFinalTotal(),
                                item.taxRatePercent()
                        )).collect(Collectors.toList());

                ApprovalGateService.GateDecision gate = b2bCompanyId != null
                        ? approvalGateService.evaluate(b2bCompanyId, intent.lockedTotal(),
                                intent.pricedCart().items().stream()
                                        .map(com.builddash.backend.domain.model.PricedCartLineItem::productId)
                                        .toList(),
                                b2bSiteId)
                        : ApprovalGateService.GateDecision.notGated();

                // Gated orders are born PENDING_APPROVAL — never PAYMENT_PENDING first — and
                // hold no delivery slot: the gate releases what createIntent just acquired.
                Order newOrder = new Order(
                        UUID.randomUUID(),
                        userId,
                        addressId,
                        slotId,
                        slotDate,
                        intent.lockedTotal(),
                        gate.gated() ? OrderStatus.PENDING_APPROVAL : OrderStatus.PAYMENT_PENDING,
                        gate.gated() ? null : intent.deliverySlotLockId(),
                        java.time.Instant.now(),
                        null,
                        null,
                        lineItems,
                        intent.pricedCart().companyId(),
                        b2bSiteId,
                        null
                );

                Order saved = orderRepository.save(newOrder);
                idempotencyKeyRepository.save(idempotencyKey, saved.id());
                recordCouponRedemptions(userId, intent.pricedCart(), saved.id());
                if (gate.gated()) {
                    approvalGateService.openApproval(saved, gate, intent.deliverySlotLockId());
                } else {
                    // H1.2/H1.3: a durable PENDING(no transactionId) claim commits in the SAME
                    // transaction as the order, before the gateway is ever called. A crash
                    // after this point leaves evidence of the attempt (this row) instead of an
                    // order with no payment trace at all.
                    pendingClaim[0] = new Payment(
                            UUID.randomUUID(), saved.id(), null, saved.totalAmount(),
                            com.builddash.backend.domain.enums.PaymentStatus.PENDING, null);
                    paymentRepository.save(pendingClaim[0]);
                }

                return saved;
            });
        } catch (org.springframework.dao.DataIntegrityViolationException | org.springframework.transaction.TransactionException e) {
            // Concurrent double-submit: this thread lost the PK race on idempotency_keys.
            // The entire losing transaction rolled back in Postgres (no orphan order, no orphan slot lock).
            // Fetch the winning order created by the winning thread in a fresh transaction.
            savedOrder = existingOrderForKey(idempotencyKey, userId);
            pendingClaim[0] = null;
        }

        if (savedOrder.status() == OrderStatus.PENDING_APPROVAL) {
            // No gateway, no Payment row, paymentUrl null — approval resumes payment.
            // H2.1: a claimed B2B draft is already consumed via consumed_at — clearCart
            // only resolves PRIMARY carts and would otherwise create a junk PRIMARY cart.
            if (!isB2bDraftCheckout[0]) {
                cartService.clearCart(userId, draftProjectId[0]);
            }
            return new OrderResult(savedOrder, null);
        }

        if (pendingClaim[0] == null) {
            // H1.3b: an idempotent retry of an already-created order (same key resolved
            // to an existing order, or this thread lost the concurrent-insert race).
            // Never re-enter the gateway for an order that may already have a payment
            // attempt in flight — a PENDING claim with no transactionId is an unknown
            // external state, not a green light for a second gateway session. Surface
            // whatever payment attempt already exists (possibly none yet, possibly no
            // paymentUrl yet) rather than initiating a second one.
            Optional<Payment> existingPayment = paymentRepository.findLatestByOrderId(savedOrder.id());
            cartService.clearCart(userId, cartId != null ? draftProjectId[0] : null);
            return new OrderResult(savedOrder, existingPayment.map(Payment::paymentUrl).orElse(null));
        }

        // Only the gateway call maps to PaymentGatewayException — a DB failure after a
        // successful initiate must surface as itself, not be mislabeled "gateway down".
        // Shared with retryPayment/initiatePaymentForApprovedOrder: gateway outside any
        // transaction, then a short transaction records the outcome.
        PaymentReference ref = completePaymentInitiation(pendingClaim[0]);

        // H2.1: skip for a claimed B2B draft — see the PENDING_APPROVAL branch above.
        if (!isB2bDraftCheckout[0]) {
            cartService.clearCart(userId, cartId != null ? draftProjectId[0] : null);
        }

        return new OrderResult(savedOrder, ref.paymentUrl());
    }

    private Order existingOrderForKey(String idempotencyKey, UUID userId) {
        // Race-retry path: the winner inserted this key moments ago, so the window filter
        // can never miss it — same cutoff discipline as the primary read for consistency.
        Instant idempotencyCutoff = Instant.now()
                .minus(Duration.ofHours(idempotencyProperties.getIdempotencyWindowHours()));
        UUID orderId = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            Optional<UUID> orderIdOpt = idempotencyKeyRepository.findOrderId(idempotencyKey, idempotencyCutoff);
            if (orderIdOpt.isPresent()) {
                orderId = orderIdOpt.get();
                Optional<Order> orderOpt = orderRepository.findById(orderId);
                if (orderOpt.isPresent()) {
                    Order order = orderOpt.get();
                    if (!order.userId().equals(userId)) {
                        throw new com.builddash.backend.domain.exception.ForbiddenException("FORBIDDEN", "User does not own this order");
                    }
                    return order;
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (orderId == null) {
            orderId = idempotencyKeyRepository.findOrderId(idempotencyKey, idempotencyCutoff)
                    .orElseThrow(() -> new IllegalStateException("Order not found for idempotency key: " + idempotencyKey));
        }
        final UUID resolvedOrderId = orderId;
        Order order = orderRepository.findById(resolvedOrderId)
                .orElseThrow(() -> new IllegalStateException("Order not found for idempotency key: " + resolvedOrderId));
        if (!order.userId().equals(userId)) {
            throw new com.builddash.backend.domain.exception.ForbiddenException("FORBIDDEN", "User does not own this order");
        }
        return order;
    }

    /**
     * Persists one redemption row per applied coupon so maxUsesPerUser limits hold.
     * Records the requested code — item coupons count as used even if a later step
     * (e.g. margin floor) absorbed the discount; over-counting is the safe direction.
     */
    private void recordCouponRedemptions(UUID userId, com.builddash.backend.domain.model.PricedCart pricedCart, UUID orderId) {
        java.util.Set<String> codes = new java.util.LinkedHashSet<>();
        if (pricedCart.appliedCartCoupon() != null) {
            codes.add(pricedCart.appliedCartCoupon());
        }
        pricedCart.items().stream()
                .map(com.builddash.backend.domain.model.PricedCartLineItem::appliedItemCoupon)
                .filter(code -> code != null && !code.isBlank())
                .forEach(codes::add);

        for (String code : codes) {
            couponRepository.findByCode(code)
                    .ifPresent(coupon -> couponRedemptionRepository.record(userId, coupon.getId(), orderId));
        }
    }

    @Override
    public OrderResult retryPayment(UUID userId, UUID orderId) {
        // Tx 1: lock the order row against sweep/webhook races, validate, insert pending payment
        final Order[] orderHolder = new Order[1];
        com.builddash.backend.domain.model.Payment savedPayment = transactionTemplate.execute(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException("Order", orderId.toString()));

            if (!order.userId().equals(userId)) {
                throw new com.builddash.backend.domain.exception.ForbiddenException("FORBIDDEN", "User does not own this order");
            }
            if (order.status() != OrderStatus.PAYMENT_PENDING) {
                throw new InvalidOrderStateException(order.status());
            }
            Optional<com.builddash.backend.domain.model.Payment> latestPayment = paymentRepository.findLatestByOrderId(orderId);
            if (latestPayment.isPresent() && latestPayment.get().status() == com.builddash.backend.domain.enums.PaymentStatus.PENDING) {
                throw new PaymentRetryInProgressException();
            }

            orderHolder[0] = order;
            return paymentRepository.save(new com.builddash.backend.domain.model.Payment(
                    UUID.randomUUID(), order.id(), null, order.totalAmount(),
                    com.builddash.backend.domain.enums.PaymentStatus.PENDING, null
            ));
        });
        Order order = orderHolder[0];

        PaymentReference ref = completePaymentInitiation(savedPayment);

        return new OrderResult(order, ref.paymentUrl());
    }

    @Override
    public OrderResult initiatePaymentForApprovedOrder(UUID orderId) {
        // retryPayment minus the ownership check — the approver already cleared critical
        // B2B authorization; the order's own user has no involvement in this call. The
        // existing guards still hold: PAYMENT_PENDING only, at most one PENDING payment.
        final Order[] orderHolder = new Order[1];
        com.builddash.backend.domain.model.Payment savedPayment = transactionTemplate.execute(status -> {
            Order order = orderRepository.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException("Order", orderId.toString()));
            if (order.status() != OrderStatus.PAYMENT_PENDING) {
                throw new InvalidOrderStateException(order.status());
            }
            Optional<com.builddash.backend.domain.model.Payment> latestPayment = paymentRepository.findLatestByOrderId(orderId);
            if (latestPayment.isPresent() && latestPayment.get().status() == com.builddash.backend.domain.enums.PaymentStatus.PENDING) {
                throw new PaymentRetryInProgressException();
            }
            orderHolder[0] = order;
            return paymentRepository.save(new com.builddash.backend.domain.model.Payment(
                    UUID.randomUUID(), order.id(), null, order.totalAmount(),
                    com.builddash.backend.domain.enums.PaymentStatus.PENDING, null
            ));
        });

        PaymentReference ref = completePaymentInitiation(savedPayment);

        return new OrderResult(orderHolder[0], ref.paymentUrl());
    }

    /**
     * Shared gateway tail (9-D extraction, retryPayment behavior byte-identical): gateway
     * outside any tx so no row lock is held during the network call; failure marks the
     * PENDING payment FAILED in a fresh tx and surfaces PaymentGatewayException.
     */
    private PaymentReference completePaymentInitiation(com.builddash.backend.domain.model.Payment pendingPayment) {
        UUID orderId = pendingPayment.orderId();
        final PaymentReference ref;
        try {
            ref = paymentGateway.initiate(orderId, pendingPayment.amount());
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(status -> paymentRepository.save(
                    new com.builddash.backend.domain.model.Payment(
                            pendingPayment.id(), orderId, null, pendingPayment.amount(),
                            com.builddash.backend.domain.enums.PaymentStatus.FAILED, null)));
            throw new PaymentGatewayException(orderId, e.getMessage());
        }

        // Tx 2: record transaction id + payment URL so the client can actually pay
        transactionTemplate.executeWithoutResult(status -> paymentRepository.save(
                new com.builddash.backend.domain.model.Payment(
                        pendingPayment.id(), orderId, ref.transactionId(), pendingPayment.amount(),
                        com.builddash.backend.domain.enums.PaymentStatus.PENDING, ref.paymentUrl())));

        return ref;
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(UUID userId, UUID orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException("Order", orderId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> listOrders(UUID userId) {
        return orderRepository.findAllByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> listOrders(UUID userId, int page, int size) {
        return orderRepository.findAllByUserId(userId, page, size);
    }

    @Override
    @Transactional
    public ReorderResult reorder(UUID userId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.userId().equals(userId))
                .orElseThrow(() -> new com.builddash.backend.domain.exception.NotFoundException("Order", orderId.toString()));

        if (order.companyId() != null) {
            // H2.2: reorder() builds a companyId=null REORDER_SCRATCH cart with no way to
            // carry B2B scope — silently re-entering the B2C path would bypass approval
            // and contract pricing. Reject explicitly instead.
            throw new BadRequestException("B2B_REORDER_UNSUPPORTED",
                    "B2B orders cannot be reordered; re-create via RFQ/PO conversion instead");
        }

        List<CartLineItem> cartItems = order.lineItems().stream()
                .map(li -> new CartLineItem(
                        UUID.randomUUID(),
                        null,
                        li.productId(),
                        li.quantity(),
                        null
                ))
                .toList();

        com.builddash.backend.domain.model.PricedCart pricedCart = cartService.createReorderCart(userId, cartItems);

        return new ReorderResult(pricedCart.id(), "Items added to cart");
    }
}
