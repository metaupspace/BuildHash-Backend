package com.builddash.backend.application.impl;

import com.builddash.backend.application.service.B2bAuthorizer;
import com.builddash.backend.application.service.CartService;
import com.builddash.backend.application.service.PoImportService;
import com.builddash.backend.domain.enums.CompanyPermission;
import com.builddash.backend.domain.enums.PoImportStatus;
import com.builddash.backend.domain.enums.PoRowStatus;
import com.builddash.backend.domain.exception.PoImportValidationException;
import com.builddash.backend.domain.model.CartLineItem;
import com.builddash.backend.domain.model.PoImport;
import com.builddash.backend.domain.model.PoImportRow;
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.model.Product;
import com.builddash.backend.domain.port.PoImportRepository;
import com.builddash.backend.domain.port.PoImportRowRepository;
import com.builddash.backend.domain.port.PoWorkbookParser;
import com.builddash.backend.domain.port.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PoImportServiceImplTest {

    private static final byte[] XLSX = {0x50, 0x4B, 0x03, 0x04, 1, 2, 3};

    private B2bAuthorizer b2bAuthorizer;
    private CartService cartService;
    private PoImportRepository poImportRepository;
    private PoImportRowRepository poImportRowRepository;
    private PoWorkbookParser parser;
    private ProductRepository productRepository;
    private PoImportServiceImpl service;

    private UUID userId;
    private UUID companyId;
    private UUID productId;
    private String slug;

    @BeforeEach
    void setUp() {
        b2bAuthorizer = mock(B2bAuthorizer.class);
        cartService = mock(CartService.class);
        poImportRepository = mock(PoImportRepository.class);
        poImportRowRepository = mock(PoImportRowRepository.class);
        parser = mock(PoWorkbookParser.class);
        productRepository = mock(ProductRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        doAnswer(inv -> {
            ((Consumer<?>) inv.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        service = new PoImportServiceImpl(b2bAuthorizer, cartService, poImportRepository,
                poImportRowRepository, parser, productRepository, transactionTemplate);

        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        productId = UUID.randomUUID();
        slug = "cement-bag";
        lenient().when(poImportRepository.findByCompanyIdAndIdempotencyKey(companyId, "key"))
                .thenReturn(Optional.empty());
        lenient().when(poImportRepository.save(any(PoImport.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(poImportRowRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(poImportRowRepository.findByImportId(any(UUID.class))).thenReturn(List.of());
        lenient().when(productRepository.findBySlug(slug)).thenReturn(List.of(product(slug, productId)));
        lenient().when(cartService.createB2bDraftCart(any(), any(), any(), anyList()))
                .thenAnswer(inv -> new PricedCart(UUID.randomUUID(), userId, null, List.of(),
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.TEN, null, null, companyId));
    }

    private Product product(String slug, UUID id) {
        Product product = new Product();
        product.setId(id);
        product.setSlug(slug);
        return product;
    }

    private MultipartFile file() {
        return new MockMultipartFile("file", "po.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", XLSX);
    }

    private PoWorkbookParser.PoRawRow row(String slug, Long qty, String errorCode) {
        return new PoWorkbookParser.PoRawRow(1, slug, qty, errorCode);
    }

    @Test
    void import_happyPath_persistsRowsAndCreatesDraftCart() throws Exception {
        when(parser.parse(any(InputStream.class))).thenReturn(List.of(
                row(slug, 10L, null),
                row(slug, 5L, null),           // duplicate slug: merged
                row("ghost", 1L, null),        // unknown slug: INVALID row persisted
                row("bad", 20L, "CELL_TYPE_INVALID")));
        UUID ghostProductId = UUID.randomUUID();
        when(productRepository.findBySlug("ghost")).thenReturn(List.of());

        PoImportService.ImportResult result =
                service.importWorkbook(userId, companyId, "key", file());

        assertThat(result.replay()).isFalse();
        assertThat(result.poImport().status()).isEqualTo(PoImportStatus.REVIEW);
        assertThat(result.poImport().totalRows()).isEqualTo(4);
        assertThat(result.poImport().validRows()).isEqualTo(2);
        assertThat(result.poImport().invalidRows()).isEqualTo(2);
        assertThat(result.poImport().draftCartId()).isNotNull();

        // Every source row persisted — invalid ones included, verbatim
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PoImportRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(poImportRowRepository).saveAll(rowsCaptor.capture());
        List<PoImportRow> saved = rowsCaptor.getValue();
        assertThat(saved).hasSize(4);
        assertThat(saved.stream().filter(r -> r.status() == PoRowStatus.INVALID)
                .map(PoImportRow::errorCode))
                .containsExactlyInAnyOrder("PRODUCT_SLUG_NOT_FOUND", "CELL_TYPE_INVALID");

        // Merged cart lines: one line per slug, quantities summed
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CartLineItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(cartService).createB2bDraftCart(eq(companyId), eq(userId), any(UUID.class),
                itemsCaptor.capture());
        assertThat(itemsCaptor.getValue()).hasSize(1);
        assertThat(itemsCaptor.getValue().get(0).productId()).isEqualTo(productId);
        assertThat(itemsCaptor.getValue().get(0).quantity()).isEqualTo(15);
    }

    @Test
    void import_mergedQuantityOverCap_invalidatesContributors() throws Exception {
        when(parser.parse(any(InputStream.class))).thenReturn(List.of(
                row(slug, 6000L, null),
                row(slug, 6000L, null)));

        PoImportService.ImportResult result =
                service.importWorkbook(userId, companyId, "key", file());

        assertThat(result.poImport().validRows()).isZero();
        assertThat(result.poImport().invalidRows()).isEqualTo(2);
        assertThat(result.poImport().draftCartId()).isNull(); // zero valid: no cart, stays REVIEW
        verify(cartService, never()).createB2bDraftCart(any(), any(), any(), anyList());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PoImportRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(poImportRowRepository).saveAll(rowsCaptor.capture());
        assertThat(rowsCaptor.getValue()).allMatch(r ->
                r.status() == PoRowStatus.INVALID && "QTY_INVALID".equals(r.errorCode()));
    }

    @Test
    void import_rowLevelValidation() throws Exception {
        when(parser.parse(any(InputStream.class))).thenReturn(List.of(
                row("x".repeat(300), 1L, null),   // overlong slug
                row(slug, 0L, null),              // qty below range
                row(slug, 10001L, null),          // qty above range
                row(null, null, "SKU_REQUIRED")));

        PoImportService.ImportResult result =
                service.importWorkbook(userId, companyId, "key", file());

        assertThat(result.poImport().validRows()).isZero();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PoImportRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(poImportRowRepository).saveAll(rowsCaptor.capture());
        List<String> codes = rowsCaptor.getValue().stream()
                .map(PoImportRow::errorCode).toList();
        assertThat(codes).containsExactly("SKU_INVALID", "QTY_INVALID", "QTY_INVALID", "SKU_REQUIRED");
    }

    @Test
    void import_ambiguousSlug_neverFirstMatch() throws Exception {
        when(parser.parse(any(InputStream.class))).thenReturn(List.of(row(slug, 1L, null)));
        when(productRepository.findBySlug(slug)).thenReturn(List.of(
                product(slug, UUID.randomUUID()), product(slug, UUID.randomUUID())));

        PoImportService.ImportResult result =
                service.importWorkbook(userId, companyId, "key", file());

        assertThat(result.poImport().invalidRows()).isEqualTo(1);
        assertThat(result.poImport().draftCartId()).isNull();
    }

    @Test
    void import_structuralFailure_persistsFailedStructureAndConsumesKey() throws Exception {
        when(parser.parse(any(InputStream.class)))
                .thenThrow(new PoImportValidationException("INVALID_WORKBOOK", "bad"));

        assertThatThrownBy(() -> service.importWorkbook(userId, companyId, "key", file()))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("INVALID_WORKBOOK");

        ArgumentCaptor<PoImport> captor = ArgumentCaptor.forClass(PoImport.class);
        verify(poImportRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(PoImportStatus.FAILED_STRUCTURE);
        verify(poImportRowRepository, never()).saveAll(anyList());
        verify(cartService, never()).createB2bDraftCart(any(), any(), any(), anyList());
    }

    @Test
    void import_replaySameKey_returnsExistingWithoutReparse() throws Exception {
        PoImport existing = new PoImport(UUID.randomUUID(), companyId, "key", userId,
                PoImportStatus.FAILED_STRUCTURE, 0, 0, 0, null, Instant.now(), Instant.now());
        when(poImportRepository.findByCompanyIdAndIdempotencyKey(companyId, "key"))
                .thenReturn(Optional.of(existing));

        PoImportService.ImportResult result =
                service.importWorkbook(userId, companyId, "key", file());

        assertThat(result.replay()).isTrue();
        assertThat(result.poImport()).isEqualTo(existing);
        verify(parser, never()).parse(any(InputStream.class)); // no reparse, no storage read
        verify(poImportRowRepository, never()).saveAll(anyList());
    }

    @Test
    void import_idempotencyRaceLoser_returnsWinner() throws Exception {
        when(parser.parse(any(InputStream.class))).thenReturn(List.of(row(slug, 1L, null)));
        PoImport winner = new PoImport(UUID.randomUUID(), companyId, "key", userId,
                PoImportStatus.REVIEW, 1, 1, 0, UUID.randomUUID(), Instant.now(), Instant.now());
        when(poImportRepository.save(any(PoImport.class)))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenReturn(winner);
        when(poImportRepository.findByCompanyIdAndIdempotencyKey(companyId, "key"))
                .thenReturn(Optional.empty())            // pre-read misses (race)
                .thenReturn(Optional.of(winner));        // post-DIVE re-read finds winner

        PoImportService.ImportResult result =
                service.importWorkbook(userId, companyId, "key", file());

        assertThat(result.replay()).isTrue();
        assertThat(result.poImport()).isEqualTo(winner);
    }

    @Test
    void import_blankIdempotencyKey_rejected() {
        assertThatThrownBy(() -> service.importWorkbook(userId, companyId, " ", file()))
                .isInstanceOf(PoImportValidationException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    @Test
    void import_authorizesWithPoUpload() throws Exception {
        when(parser.parse(any(InputStream.class))).thenReturn(List.of());

        service.importWorkbook(userId, companyId, "key", file());

        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.PO_UPLOAD, null, true);
    }
}
