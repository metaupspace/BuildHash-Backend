package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.PoConversionService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.PoImportStatus;
import com.builddash.backend.domain.enums.PoRowStatus;
import com.builddash.backend.domain.exception.InvalidPoStateException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.PoImport;
import com.builddash.backend.domain.model.PoImportRow;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.PoImportRepository;
import com.builddash.backend.domain.port.PoImportRowRepository;
import com.builddash.backend.domain.port.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Single locked transaction (RFQ-convert discipline): lock import row ->
 * authorize(PO_CONVERT, company-wide) -> state gate -> reuse-or-create the
 * B2B_DRAFT cart -> CONVERTED. The cart normally already exists (created with
 * the import); the create path is defensive so a REVIEW import with valid rows
 * can never end up unconvertible.
 */
@Service
@RequiredArgsConstructor
public class PoConversionServiceImpl implements PoConversionService {

    private final B2bAuthorizer b2bAuthorizer;
    private final CartService cartService;
    private final PoImportRepository poImportRepository;
    private final PoImportRowRepository poImportRowRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public UUID convert(UUID userId, UUID importId) {
        PoImport locked = poImportRepository.findByIdForUpdate(importId)
                .orElseThrow(() -> new NotFoundException("PO_IMPORT_NOT_FOUND",
                        "PO import not found: " + importId));
        b2bAuthorizer.authorize(userId, locked.companyId(), CompanyPermission.PO_CONVERT, null, true);

        if (locked.status() == PoImportStatus.CONVERTED) {
            return locked.draftCartId(); // idempotent: never a second draft cart
        }
        if (locked.status() != PoImportStatus.REVIEW) {
            throw new InvalidPoStateException("PO_IMPORT_NOT_REVIEW",
                    "Import is not reviewable, current status: " + locked.status());
        }

        List<PoImportRow> rows = poImportRowRepository.findByImportId(importId);
        List<PoImportRow> validRows = rows.stream()
                .filter(r -> r.status() == PoRowStatus.VALID)
                .toList();
        if (validRows.isEmpty()) {
            // Zero valid rows: import stays REVIEW, no cart, conversion rejected.
            throw new InvalidPoStateException("NO_VALID_ROWS",
                    "Import has no valid rows to convert");
        }

        UUID cartId = locked.draftCartId();
        if (cartId == null) {
            // Defensive: the import should carry its cart since upload. Merge by
            // slug first — cart line items are unique per (cart, product).
            java.util.Map<String, Integer> merged = new java.util.LinkedHashMap<>();
            validRows.forEach(r -> merged.merge(r.productSlug(), safeQuantity(r), Integer::sum));
            List<CartLineItem> items = merged.entrySet().stream()
                    .map(e -> new CartLineItem(null, null, productIdFor(e.getKey()),
                            e.getValue(), null))
                    .toList();
            cartId = cartService
                    .createB2bDraftCart(locked.companyId(), userId, locked.id(), items)
                    .id();
        }

        poImportRepository.save(locked.converted(cartId));
        return cartId;
    }

    private UUID productIdFor(String productSlug) {
        List<Product> matches = productRepository.findBySlug(productSlug);
        if (matches.size() != 1) {
            // A VALID row resolved to exactly one product at upload; if catalog
            // data changed underneath, fail closed rather than guess.
            throw new InvalidPoStateException("PRODUCT_SLUG_UNRESOLVED",
                    "Product for slug " + productSlug + " is no longer unambiguous");
        }
        return matches.get(0).getId();
    }

    private int safeQuantity(PoImportRow row) {
        return row.quantity() == null ? 0 : row.quantity();
    }
}
