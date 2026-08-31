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
import com.builddash.backend.domain.model.PricedCart;
import com.builddash.backend.domain.model.Product;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RfqServiceImplTest {

    private B2bAuthorizer b2bAuthorizer;
    private CartService cartService;
    private RfqRepository rfqRepository;
    private RfqItemRepository rfqItemRepository;
    private RfqQuoteRepository rfqQuoteRepository;
    private RfqRouteRepository rfqRouteRepository;
    private VendorRepository vendorRepository;
    private ProductRepository productRepository;
    private RfqService rfqService;

    private UUID userId;
    private UUID companyId;
    private UUID productId;
    private UUID vendorId;
    private Instant future;

    @BeforeEach
    void setUp() {
        b2bAuthorizer = mock(B2bAuthorizer.class);
        cartService = mock(CartService.class);
        rfqRepository = mock(RfqRepository.class);
        rfqItemRepository = mock(RfqItemRepository.class);
        rfqQuoteRepository = mock(RfqQuoteRepository.class);
        rfqRouteRepository = mock(RfqRouteRepository.class);
        vendorRepository = mock(VendorRepository.class);
        productRepository = mock(ProductRepository.class);
        rfqService = new RfqServiceImpl(b2bAuthorizer, cartService, rfqRepository, rfqItemRepository,
                rfqQuoteRepository, rfqRouteRepository, vendorRepository, productRepository);

        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        productId = UUID.randomUUID();
        vendorId = UUID.randomUUID();
        future = Instant.now().plusSeconds(3600);

        lenient().when(productRepository.findById(productId))
                .thenReturn(Optional.of(mock(Product.class)));
        lenient().when(rfqItemRepository.save(any(RfqItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- creation ----

    @Test
    void create_emptyItems_rejected422() {
        assertThatThrownBy(() -> rfqService.create(userId, companyId, future, null, List.of()))
                .isInstanceOf(RfqValidationException.class)
                .extracting("code")
                .isEqualTo("RFQ_ITEMS_REQUIRED");
    }

    @Test
    void create_missingProduct_throwsNotFound() {
        UUID missing = UUID.randomUUID();
        when(productRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rfqService.create(userId, companyId, future, null,
                List.of(new RfqService.ItemCommand(missing, 5))))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void create_nonPositiveQuantity_rejected422() {
        assertThatThrownBy(() -> rfqService.create(userId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 0))))
                .isInstanceOf(RfqValidationException.class)
                .extracting("code")
                .isEqualTo("RFQ_QUANTITY_INVALID");
    }

    @Test
    void create_pastExpiry_rejected422() {
        assertThatThrownBy(() -> rfqService.create(userId, companyId, Instant.now().minusSeconds(60),
                null, List.of(new RfqService.ItemCommand(productId, 5))))
                .isInstanceOf(RfqValidationException.class)
                .extracting("code")
                .isEqualTo("RFQ_EXPIRY_INVALID");
    }

    @Test
    void create_routedVendorsPersisted_asSnapshot() {
        UUID otherVendor = UUID.randomUUID();
        when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepository.findRoutableVendors(List.of(productId))).thenReturn(List.of(
                new Vendor(vendorId, "A", true, List.of(), null, null),
                new Vendor(otherVendor, "B", true, List.of(), null, null)));

        Rfq result = rfqService.create(userId, companyId, future, "notes",
                List.of(new RfqService.ItemCommand(productId, 5)));

        assertThat(result.status()).isEqualTo(RfqStatus.OPEN);
        assertThat(result.routedVendorIds()).containsExactlyInAnyOrder(vendorId, otherVendor);
        verify(rfqRouteRepository).saveAll(result.id(), List.of(vendorId, otherVendor));
        verify(rfqItemRepository).save(any(RfqItem.class));
        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.RFQ_CREATE, null, true);
    }

    @Test
    void create_noMatchingVendors_staysOpenWithEmptyRoutes() {
        when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepository.findRoutableVendors(List.of(productId))).thenReturn(List.of());

        Rfq result = rfqService.create(userId, companyId, future, null,
                List.of(new RfqService.ItemCommand(productId, 5)));

        assertThat(result.status()).isEqualTo(RfqStatus.OPEN);
        assertThat(result.routedVendorIds()).isEmpty();
        verify(rfqRouteRepository).saveAll(result.id(), List.of());
    }

    // ---- read / compare ----

    @Test
    void get_authorizesWithRfqView() {
        Rfq rfq = openRfq();
        when(rfqRepository.findById(rfq.id())).thenReturn(Optional.of(rfq));

        assertThat(rfqService.get(userId, rfq.id())).isEqualTo(rfq);
        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.RFQ_VIEW, null, false);
    }

    @Test
    void listQuotes_ordersByAmountAscendingAndComputesExpired() {
        Rfq rfq = openRfq();
        when(rfqRepository.findById(rfq.id())).thenReturn(Optional.of(rfq));
        RfqQuote cheap = quote(new BigDecimal("100.00"), future);
        RfqQuote expensive = quote(new BigDecimal("200.00"), Instant.now().minusSeconds(1));
        when(rfqQuoteRepository.findByRfqIdOrderByTotalAmountAsc(rfq.id()))
                .thenReturn(List.of(cheap, expensive)); // repository already sorts
        when(vendorRepository.findById(vendorId)).thenReturn(Optional.of(
                new Vendor(vendorId, "V", true, List.of(), null, null)));

        List<RfqService.QuoteComparison> result = rfqService.listQuotes(userId, rfq.id());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).quote()).isEqualTo(cheap);
        assertThat(result.get(0).expired()).isFalse();
        assertThat(result.get(1).expired()).isTrue(); // expired quotes retained historically
        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.QUOTE_VIEW, null, false);
    }

    // ---- cancel ----

    @Test
    void cancel_terminalRfq_rejected409() {
        Rfq cancelled = openRfq().withStatus(RfqStatus.CANCELLED);
        when(rfqRepository.findByIdForUpdate(cancelled.id())).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> rfqService.cancel(userId, cancelled.id()))
                .isInstanceOf(InvalidRfqStateException.class)
                .extracting("code")
                .isEqualTo("RFQ_NOT_OPEN");
    }

    @Test
    void cancel_openRfq_transitionsToCancelled() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> inv.getArgument(0));

        Rfq result = rfqService.cancel(userId, rfq.id());

        assertThat(result.status()).isEqualTo(RfqStatus.CANCELLED);
        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.RFQ_CANCEL, null, true);
    }

    // ---- convert ----

    @Test
    void convert_notOpen_rejected409() {
        Rfq expired = openRfq().withStatus(RfqStatus.EXPIRED);
        when(rfqRepository.findByIdForUpdate(expired.id())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> rfqService.convert(userId, expired.id(), UUID.randomUUID()))
                .isInstanceOf(InvalidRfqStateException.class)
                .extracting("code")
                .isEqualTo("RFQ_NOT_OPEN");
    }

    @Test
    void convert_quoteOfAnotherRfq_throwsNotFound() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        RfqQuote foreignQuote = new RfqQuote(UUID.randomUUID(), UUID.randomUUID(), vendorId,
                new BigDecimal("10.00"), future,
                com.builddash.backend.domain.enums.RfqQuoteStatus.SUBMITTED, Instant.now());
        when(rfqQuoteRepository.findById(foreignQuote.id())).thenReturn(Optional.of(foreignQuote));

        assertThatThrownBy(() -> rfqService.convert(userId, rfq.id(), foreignQuote.id()))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo("QUOTE_NOT_FOUND");
    }

    @Test
    void convert_expiredQuote_rejected422() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        RfqQuote expired = new RfqQuote(UUID.randomUUID(), rfq.id(), vendorId,
                new BigDecimal("10.00"), Instant.now().minusSeconds(1),
                com.builddash.backend.domain.enums.RfqQuoteStatus.SUBMITTED, Instant.now());
        when(rfqQuoteRepository.findById(expired.id())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> rfqService.convert(userId, rfq.id(), expired.id()))
                .isInstanceOf(QuoteValidationException.class)
                .extracting("code")
                .isEqualTo("QUOTE_EXPIRED");
    }

    @Test
    void convert_happyPath_createsB2bDraftCartAndMarksConverted() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        when(rfqRepository.save(any(Rfq.class))).thenAnswer(inv -> inv.getArgument(0));
        UUID quoteId = UUID.randomUUID();
        RfqQuote quote = new RfqQuote(quoteId, rfq.id(), vendorId, new BigDecimal("10.00"),
                future, com.builddash.backend.domain.enums.RfqQuoteStatus.SUBMITTED, Instant.now());
        when(rfqQuoteRepository.findById(quoteId)).thenReturn(Optional.of(quote));
        UUID cartId = UUID.randomUUID();
        when(cartService.createB2bDraftCart(eq(companyId), eq(userId), eq(rfq.id()), any()))
                .thenReturn(new PricedCart(cartId, userId, rfq.id(), List.of(),
                        BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.TEN, null, null, companyId));

        RfqService.ConversionResult result = rfqService.convert(userId, rfq.id(), quoteId);

        assertThat(result.rfq().status()).isEqualTo(RfqStatus.CONVERTED);
        assertThat(result.cartId()).isEqualTo(cartId);
        verify(cartService).createB2bDraftCart(eq(companyId), eq(userId), eq(rfq.id()),
                argThat(items -> items.size() == 1
                        && ((CartLineItem) items.get(0)).productId().equals(productId)
                        && ((CartLineItem) items.get(0)).quantity() == 5));
        verify(b2bAuthorizer).authorize(userId, companyId, CompanyPermission.RFQ_CONVERT, null, true);
    }

    // ---- quote submission (application admin path) ----

    @Test
    void submitQuote_vendorMissing_throwsNotFound() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        when(vendorRepository.findById(vendorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rfqService.submitQuote(rfq.id(), vendorId, BigDecimal.TEN, future))
                .isInstanceOf(NotFoundException.class)
                .extracting("code")
                .isEqualTo("VENDOR_NOT_FOUND");
    }

    @Test
    void submitQuote_inactiveVendor_rejected422() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        when(vendorRepository.findById(vendorId)).thenReturn(Optional.of(
                new Vendor(vendorId, "V", false, List.of(), null, null)));

        assertThatThrownBy(() -> rfqService.submitQuote(rfq.id(), vendorId, BigDecimal.TEN, future))
                .isInstanceOf(QuoteValidationException.class)
                .extracting("code")
                .isEqualTo("VENDOR_INACTIVE");
    }

    @Test
    void submitQuote_unroutedVendor_rejected422() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        when(vendorRepository.findById(vendorId)).thenReturn(Optional.of(
                new Vendor(vendorId, "V", true, List.of(), null, null)));
        when(rfqRouteRepository.existsByRfqIdAndVendorId(rfq.id(), vendorId)).thenReturn(false);

        assertThatThrownBy(() -> rfqService.submitQuote(rfq.id(), vendorId, BigDecimal.TEN, future))
                .isInstanceOf(VendorNotRoutableException.class)
                .extracting("code")
                .isEqualTo("VENDOR_NOT_ROUTED");
    }

    @Test
    void submitQuote_pastValidity_rejected422() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        stubActiveRoutedVendor(rfq.id());

        assertThatThrownBy(() -> rfqService.submitQuote(rfq.id(), vendorId, BigDecimal.TEN,
                Instant.now().minusSeconds(1)))
                .isInstanceOf(QuoteValidationException.class)
                .extracting("code")
                .isEqualTo("QUOTE_VALIDITY_INVALID");
    }

    @Test
    void submitQuote_duplicateRejected_preCheck() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        stubActiveRoutedVendor(rfq.id());
        when(rfqQuoteRepository.findByRfqIdAndVendorId(rfq.id(), vendorId)).thenReturn(Optional.of(
                quote(BigDecimal.TEN, future)));

        assertThatThrownBy(() -> rfqService.submitQuote(rfq.id(), vendorId, BigDecimal.TEN, future))
                .isInstanceOf(DuplicateQuoteException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_QUOTE");
        verify(rfqQuoteRepository, never()).save(any());
    }

    @Test
    void submitQuote_happyPath_savesSubmittedQuote() {
        Rfq rfq = openRfq();
        when(rfqRepository.findByIdForUpdate(rfq.id())).thenReturn(Optional.of(rfq));
        stubActiveRoutedVendor(rfq.id());
        when(rfqQuoteRepository.findByRfqIdAndVendorId(rfq.id(), vendorId)).thenReturn(Optional.empty());
        when(rfqQuoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RfqQuote saved = rfqService.submitQuote(rfq.id(), vendorId,
                new BigDecimal("150.00"), future);

        assertThat(saved.status()).isEqualTo(com.builddash.backend.domain.enums.RfqQuoteStatus.SUBMITTED);
        assertThat(saved.rfqId()).isEqualTo(rfq.id());
        assertThat(saved.vendorId()).isEqualTo(vendorId);
    }

    @Test
    void submitQuote_notOpenRfq_rejected409() {
        Rfq converted = openRfq().withStatus(RfqStatus.CONVERTED);
        when(rfqRepository.findByIdForUpdate(converted.id())).thenReturn(Optional.of(converted));

        assertThatThrownBy(() -> rfqService.submitQuote(converted.id(), vendorId, BigDecimal.TEN, future))
                .isInstanceOf(InvalidRfqStateException.class)
                .extracting("code")
                .isEqualTo("RFQ_NOT_OPEN");
    }

    // ---- helpers ----

    private Rfq openRfq() {
        UUID rfqId = UUID.randomUUID();
        return new Rfq(rfqId, companyId, userId, RfqStatus.OPEN, future, null,
                List.of(new RfqItem(UUID.randomUUID(), rfqId, productId, 5)),
                List.of(vendorId), Instant.now().minusSeconds(60), Instant.now().minusSeconds(60));
    }

    private RfqQuote quote(BigDecimal total, Instant validUntil) {
        return new RfqQuote(UUID.randomUUID(), UUID.randomUUID(), vendorId, total, validUntil,
                com.builddash.backend.domain.enums.RfqQuoteStatus.SUBMITTED, Instant.now());
    }

    private void stubActiveRoutedVendor(UUID rfqId) {
        when(vendorRepository.findById(vendorId)).thenReturn(Optional.of(
                new Vendor(vendorId, "V", true, List.of(), null, null)));
        when(rfqRouteRepository.existsByRfqIdAndVendorId(rfqId, vendorId)).thenReturn(true);
    }
}
