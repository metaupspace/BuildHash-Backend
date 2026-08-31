package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.PoImportStatus;
import com.builddash.backend.domain.enums.PoRowStatus;
import com.builddash.backend.domain.exception.InvalidPoStateException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.PoImport;
import com.builddash.backend.domain.model.PoImportRow;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.PoImportRepository;
import com.builddash.backend.domain.port.PoImportRowRepository;
import com.builddash.backend.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PoConversionServiceImplTest {

    private B2bAuthorizer b2bAuthorizer;
    private CartService cartService;
    private PoImportRepository poImportRepository;
    private PoImportRowRepository poImportRowRepository;
    private ProductRepository productRepository;
    private PoConversionServiceImpl service;

    private UUID userId;
    private UUID companyId;
    private UUID importId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        b2bAuthorizer = mock(B2bAuthorizer.class);
        cartService = mock(CartService.class);
        poImportRepository = mock(PoImportRepository.class);
        poImportRowRepository = mock(PoImportRowRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new PoConversionServiceImpl(b2bAuthorizer, cartService, poImportRepository,
                poImportRowRepository, productRepository);

        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        importId = UUID.randomUUID();
        productId = UUID.randomUUID();
        lenient().when(poImportRepository.save(any(PoImport.class))).thenAnswer(inv -> inv.getArgument(0));
        Product product = new Product();
        product.setId(productId);
        product.setSlug("cement-bag");
        lenient().when(productRepository.findBySlug("cement-bag")).thenReturn(List.of(product));
        lenient().when(cartService.createB2bDraftCart(any(), any(), any(), anyList()))
                .thenAnswer(inv -> new PricedCart(UUID.randomUUID(), userId, null, List.of(),
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.TEN, null, null, companyId));
    }

    private PoImport import_(PoImportStatus status, UUID cartId, int validRows) {
        PoImport poImport = new PoImport(importId, companyId, "key", userId, status,
                validRows, validRows, 0, cartId, Instant.now(), Instant.now());
        lenient().when(poImportRepository.findByIdForUpdate(importId)).thenReturn(Optional.of(poImport));
        lenient().when(poImportRowRepository.findByImportId(importId)).thenReturn(rows(validRows));
        return poImport;
    }

    private List<PoImportRow> rows(int valid) {
        java.util.ArrayList<PoImportRow> list = new java.util.ArrayList<>();
        for (int i = 0; i < valid; i++) {
            list.add(new PoImportRow(UUID.randomUUID(), importId, i + 2, "cement-bag", 10,
                    PoRowStatus.VALID, null));
        }
        return list;
    }

    @Test
    void convert_reviewWithCart_marksConverted_returnsCartId() {
        UUID cartId = UUID.randomUUID();
        import_(PoImportStatus.REVIEW, cartId, 2);

        UUID result = service.convert(userId, importId);

        assertThat(result).isEqualTo(cartId);
        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.PO_CONVERT, null, true);
        verify(cartService, never()).createB2bDraftCart(any(), any(), any(), anyList());
    }

    @Test
    void convert_alreadyConverted_returnsExistingCartId_noDuplicate() {
        UUID cartId = UUID.randomUUID();
        import_(PoImportStatus.CONVERTED, cartId, 1);

        assertThat(service.convert(userId, importId)).isEqualTo(cartId);
        verify(poImportRepository, never()).save(any(PoImport.class));
        verify(cartService, never()).createB2bDraftCart(any(), any(), any(), anyList());
    }

    @Test
    void convert_failedStructure_409_notReview() {
        import_(PoImportStatus.FAILED_STRUCTURE, null, 0);

        assertThatThrownBy(() -> service.convert(userId, importId))
                .isInstanceOf(InvalidPoStateException.class)
                .extracting("code").isEqualTo("PO_IMPORT_NOT_REVIEW");
    }

    @Test
    void convert_zeroValidRows_409_noValidRows_staysReview() {
        import_(PoImportStatus.REVIEW, null, 0);

        assertThatThrownBy(() -> service.convert(userId, importId))
                .isInstanceOf(InvalidPoStateException.class)
                .extracting("code").isEqualTo("NO_VALID_ROWS");
        verify(cartService, never()).createB2bDraftCart(any(), any(), any(), anyList());
    }

    @Test
    void convert_missingCart_defensiveCreate_mergesDuplicateSlugs() {
        import_(PoImportStatus.REVIEW, null, 2); // two VALID rows, same slug

        UUID result = service.convert(userId, importId);

        assertThat(result).isNotNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CartLineItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(cartService).createB2bDraftCart(eq(companyId), eq(userId), eq(importId),
                captor.capture());
        assertThat(captor.getValue()).hasSize(1); // merged: one line, summed quantity
        assertThat(captor.getValue().get(0).quantity()).isEqualTo(20);
    }

    @Test
    void convert_missingImport_404() {
        when(poImportRepository.findByIdForUpdate(importId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.convert(userId, importId))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PO_IMPORT_NOT_FOUND");
    }
}
