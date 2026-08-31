package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.RfqService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.RfqStatus;
import com.builddash.backend.domain.exception.DuplicateQuoteException;
import com.builddash.backend.domain.exception.InvalidRfqStateException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.exception.QuoteValidationException;
import com.builddash.backend.domain.exception.RfqValidationException;
import com.builddash.backend.domain.exception.VendorNotRoutableException;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.Rfq;
import com.builddash.backend.domain.model.RfqItem;
import com.builddash.backend.domain.model.RfqQuote;
import com.builddash.backend.domain.model.Vendor;
import com.builddash.backend.domain.port.ProductRepository;
import com.builddash.backend.domain.port.RfqItemRepository;
import com.builddash.backend.domain.port.RfqQuoteRepository;
import com.builddash.backend.domain.port.RfqRepository;
import com.builddash.backend.domain.port.RfqRouteRepository;
import com.builddash.backend.domain.port.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lock discipline: every RFQ-row mutation takes findByIdForUpdate FIRST and only
 * then authorizes (B2bAuthorizer critical takes the company-row lock) — one
 * global rfqs -> companies order, so concurrent RFQ operations and permission
 * mutations can queue but never deadlock.
 */
@Service
@RequiredArgsConstructor
public class RfqServiceImpl implements RfqService {

    private final B2bAuthorizer b2bAuthorizer;
    private final CartService cartService;
    private final RfqRepository rfqRepository;
    private final RfqItemRepository rfqItemRepository;
    private final RfqQuoteRepository rfqQuoteRepository;
    private final RfqRouteRepository rfqRouteRepository;
    private final VendorRepository vendorRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public Rfq create(UUID userId, UUID companyId, Instant expiresAt, String notes, List<ItemCommand> items) {
        b2bAuthorizer.authorize(userId, companyId, CompanyPermission.RFQ_CREATE, null, true);

        if (items == null || items.isEmpty()) {
            throw new RfqValidationException("RFQ_ITEMS_REQUIRED", "At least one RFQ item is required");
        }
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw new RfqValidationException("RFQ_EXPIRY_INVALID", "RFQ expiresAt must be in the future");
        }
        for (ItemCommand item : items) {
            if (item.quantity() <= 0) {
                throw new RfqValidationException("RFQ_QUANTITY_INVALID",
                        "RFQ item quantity must be positive: " + item.productId());
            }
            if (productRepository.findById(item.productId()).isEmpty()) {
                throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found: " + item.productId());
            }
        }

        // Creation-time routing snapshot: vendors matching ANY item category.
        // No matching vendors -> the RFQ simply stays OPEN with an empty route list.
        List<UUID> productIds = items.stream().map(ItemCommand::productId).toList();
        List<UUID> routedVendorIds = vendorRepository.findRoutableVendors(productIds).stream()
                .map(Vendor::id)
                .distinct()
                .toList();

        Rfq rfq = new Rfq(UUID.randomUUID(), companyId, userId, RfqStatus.OPEN, expiresAt, notes,
                List.of(), routedVendorIds, null, null);
        Rfq saved = rfqRepository.save(rfq);
        List<RfqItem> savedItems = new ArrayList<>();
        for (ItemCommand item : items) {
            savedItems.add(rfqItemRepository.save(new RfqItem(
                    UUID.randomUUID(), saved.id(), item.productId(), item.quantity())));
        }
        rfqRouteRepository.saveAll(saved.id(), routedVendorIds);

        return new Rfq(saved.id(), saved.companyId(), saved.createdByUserId(), saved.status(),
                saved.expiresAt(), saved.notes(), savedItems, routedVendorIds,
                saved.createdAt(), saved.updatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Rfq get(UUID userId, UUID rfqId) {
        Rfq rfq = loadRfq(rfqId);
        b2bAuthorizer.authorize(userId, rfq.companyId(), CompanyPermission.RFQ_VIEW, null, false);
        return rfq;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuoteComparison> listQuotes(UUID userId, UUID rfqId) {
        Rfq rfq = loadRfq(rfqId);
        b2bAuthorizer.authorize(userId, rfq.companyId(), CompanyPermission.QUOTE_VIEW, null, false);
        Instant now = Instant.now();
        return rfqQuoteRepository.findByRfqIdOrderByTotalAmountAsc(rfqId).stream()
                .map(quote -> new QuoteComparison(
                        quote,
                        vendorRepository.findById(quote.vendorId()).orElse(null),
                        quote.expired(now)))
                .toList();
    }

    @Override
    @Transactional
    public Rfq cancel(UUID userId, UUID rfqId) {
        Rfq locked = loadRfqForUpdate(rfqId);
        b2bAuthorizer.authorize(userId, locked.companyId(), CompanyPermission.RFQ_CANCEL, null, true);
        requireOpen(locked);
        return rfqRepository.save(locked.withStatus(RfqStatus.CANCELLED));
    }

    /**
     * Critical mutation. Order is locked: RFQ row lock -> authorize (membership +
     * RFQ_CONVERT against current DB state) -> OPEN check -> quote belongs to this
     * RFQ -> quote still valid -> B2B_DRAFT cart -> mark CONVERTED. All one
     * transaction: a failure anywhere leaves neither cart nor status flip.
     */
    @Override
    @Transactional
    public ConversionResult convert(UUID userId, UUID rfqId, UUID quoteId) {
        Rfq locked = loadRfqForUpdate(rfqId);
        b2bAuthorizer.authorize(userId, locked.companyId(), CompanyPermission.RFQ_CONVERT, null, true);
        requireOpen(locked);

        RfqQuote quote = rfqQuoteRepository.findById(quoteId)
                .filter(q -> q.rfqId().equals(rfqId))
                .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND",
                        "Quote not found for RFQ " + rfqId + ": " + quoteId));
        if (quote.expired(Instant.now())) {
            throw QuoteValidationException.quoteExpired();
        }

        List<CartLineItem> items = locked.items().stream()
                .map(item -> new CartLineItem(null, null, item.productId(), item.quantity(), null))
                .toList();
        UUID cartId = cartService
                .createB2bDraftCart(locked.companyId(), userId, locked.id(), items)
                .id();

        Rfq converted = rfqRepository.save(locked.withStatus(RfqStatus.CONVERTED));
        return new ConversionResult(converted, cartId);
    }

    @Override
    @Transactional
    public RfqQuote submitQuote(UUID rfqId, UUID vendorId, BigDecimal totalAmount, Instant validUntil) {
        Rfq locked = loadRfqForUpdate(rfqId);
        requireOpen(locked);

        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new NotFoundException("VENDOR_NOT_FOUND", "Vendor not found: " + vendorId));
        if (!vendor.active()) {
            throw QuoteValidationException.vendorInactive(vendorId);
        }
        if (!rfqRouteRepository.existsByRfqIdAndVendorId(rfqId, vendorId)) {
            throw new VendorNotRoutableException(vendorId, rfqId);
        }
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new QuoteValidationException("QUOTE_AMOUNT_INVALID", "Quote totalAmount must be positive");
        }
        if (validUntil == null || !validUntil.isAfter(Instant.now())) {
            throw QuoteValidationException.validityInvalid();
        }
        if (rfqQuoteRepository.findByRfqIdAndVendorId(rfqId, vendorId).isPresent()) {
            throw new DuplicateQuoteException();
        }

        return rfqQuoteRepository.save(new RfqQuote(UUID.randomUUID(), rfqId, vendorId,
                totalAmount, validUntil,
                com.builddash.backend.domain.enums.RfqQuoteStatus.SUBMITTED, Instant.now()));
    }

    private Rfq loadRfq(UUID rfqId) {
        return rfqRepository.findById(rfqId)
                .orElseThrow(() -> new NotFoundException("RFQ_NOT_FOUND", "RFQ not found: " + rfqId));
    }

    private Rfq loadRfqForUpdate(UUID rfqId) {
        return rfqRepository.findByIdForUpdate(rfqId)
                .orElseThrow(() -> new NotFoundException("RFQ_NOT_FOUND", "RFQ not found: " + rfqId));
    }

    private void requireOpen(Rfq rfq) {
        if (!rfq.isOpen()) {
            throw InvalidRfqStateException.notOpen(rfq.status().name());
        }
    }
}
