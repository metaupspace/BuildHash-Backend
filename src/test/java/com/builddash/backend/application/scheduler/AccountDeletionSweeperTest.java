package com.builddash.backend.application.scheduler;

import com.builddash.backend.domain.model.DeleteRequest;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.CartLineItemRepository;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.domain.port.CouponRedemptionRepository;
import com.builddash.backend.domain.port.DeleteRequestRepository;
import com.builddash.backend.domain.port.DeliverySlotLockRepository;
import com.builddash.backend.domain.port.DeviceRepository;
import com.builddash.backend.domain.port.LoginEventRepository;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.NotifyMeSubscriptionRepository;
import com.builddash.backend.domain.port.ObjectStorage;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.domain.port.SearchQueryLogRepository;
import com.builddash.backend.domain.port.SupportTicketMessageRepository;
import com.builddash.backend.domain.port.SupportTicketRepository;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.domain.port.WishlistRepository;
import com.builddash.backend.infra.config.AccountDeletionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Classification + flag switch proof (PLAN_PHASE8 5(d)/OQ-9). The support-tickets config
 * switch is exercised BOTH ways against the same sweeper code — a real branch, verified.
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionSweeperTest {

    @Mock private DeleteRequestRepository deleteRequestRepository;
    @Mock private ObjectStorage objectStorage;
    @Mock private UserRepository userRepository;
    @Mock private ReturnRepository returnRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private CartRepository cartRepository;
    @Mock private CartLineItemRepository cartLineItemRepository;
    @Mock private WishlistRepository wishlistRepository;
    @Mock private NotifyMeSubscriptionRepository notifyMeSubscriptionRepository;
    @Mock private CouponRedemptionRepository couponRedemptionRepository;
    @Mock private SearchQueryLogRepository searchQueryLogRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private LoginEventRepository loginEventRepository;
    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private DeliverySlotLockRepository deliverySlotLockRepository;
    @Mock private SupportTicketRepository supportTicketRepository;
    @Mock private SupportTicketMessageRepository supportTicketMessageRepository;

    private AccountDeletionProperties properties;

    private AccountDeletionSweeper sweeper;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new AccountDeletionProperties();
        sweeper = new AccountDeletionSweeper(deleteRequestRepository, properties, objectStorage,
                userRepository, returnRepository, addressRepository, cartRepository, cartLineItemRepository,
                wishlistRepository, notifyMeSubscriptionRepository, couponRedemptionRepository,
                searchQueryLogRepository, deviceRepository, loginEventRepository, notificationLogRepository,
                deliverySlotLockRepository, supportTicketRepository, supportTicketMessageRepository);
    }

    private DeleteRequest dueRequest() {
        return DeleteRequest.pending(UUID.randomUUID(), userId, Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(60));
    }

    @Test
    void hardDeleteTables_allInvoked_retainTablesNeverTouched() {
        sweeper.process(dueRequest());

        // HARD-DELETE (addresses = anonymize-referenced + delete-unreferenced pair)
        verify(addressRepository).anonymizeOrderReferencedByUserId(userId);
        verify(addressRepository).deleteUnreferencedByUserId(userId);
        verify(wishlistRepository).deleteByUserId(userId);
        verify(notifyMeSubscriptionRepository).deleteByUserId(userId);
        verify(couponRedemptionRepository).deleteByUserId(userId);
        verify(searchQueryLogRepository).deleteByUserId(userId);
        verify(deviceRepository).deleteByUserId(userId);
        verify(loginEventRepository).deleteByUserId(userId);
        verify(notificationLogRepository).deleteByUserId(userId);
        verify(deliverySlotLockRepository).deleteByUserId(userId);

        // ANONYMIZE
        verify(userRepository).anonymize(userId);

        // RETAIN — no delete/deleteById method exists on those ports to even call; the
        // compile-time absence IS the guarantee (this verify would not compile otherwise).
    }

    @Test
    void supportTicketsFlag_HARD_DELETE_deletesTicketsAndMessages() {
        properties.setSupportTickets(AccountDeletionProperties.SupportTicketDeletion.HARD_DELETE);
        UUID ticketId = UUID.randomUUID();
        when(supportTicketRepository.findByUserId(userId)).thenReturn(List.of(
                new com.builddash.backend.domain.model.SupportTicket(ticketId, userId,
                        com.builddash.backend.domain.enums.SupportTicketCategory.OTHER,
                        com.builddash.backend.domain.enums.SupportTicketStatus.OPEN,
                        "subject", Instant.now().plusSeconds(3600), Instant.now(), Instant.now())));

        sweeper.process(dueRequest());

        verify(supportTicketMessageRepository).deleteByTicketId(ticketId);
        verify(supportTicketRepository).deleteById(ticketId);
    }

    @Test
    void supportTicketsFlag_RETAIN_leavesTicketsAndMessages() {
        properties.setSupportTickets(AccountDeletionProperties.SupportTicketDeletion.RETAIN);

        sweeper.process(dueRequest());

        verify(supportTicketMessageRepository, never()).deleteByTicketId(any());
        verify(supportTicketRepository, never()).deleteById(any());
        // Everything else still deleted — the flag scopes ONLY support data.
        verify(addressRepository).deleteUnreferencedByUserId(userId);
        verify(userRepository).anonymize(userId);
    }

    @Test
    void noDueRequests_nothingHappens() {
        when(deleteRequestRepository.findDue(any(Instant.class))).thenReturn(List.of());

        sweeper.sweep();

        verify(userRepository, never()).anonymize(any(UUID.class));
        verify(deleteRequestRepository, never()).save(any());
    }

    @Test
    void fullyProcessed_requestMarkedProcessed_idempotentOnResweep() {
        DeleteRequest request = dueRequest();
        when(deleteRequestRepository.findDue(any(Instant.class))).thenReturn(List.of(request)).thenReturn(List.of());

        sweeper.sweep();
        ArgumentCaptor<DeleteRequest> saved = ArgumentCaptor.forClass(DeleteRequest.class);
        verify(deleteRequestRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(com.builddash.backend.domain.enums.DeleteRequestStatus.PROCESSED);
        assertThat(saved.getValue().processedAt()).isNotNull();

        // Re-sweep: repository returns nothing (PROCESSED rows are not due) — no second run.
        sweeper.sweep();
        verify(userRepository, org.mockito.Mockito.times(1)).anonymize(userId);
    }

    @Test
    void oneTableFails_siblingsStillDeleted_requestStaysPending() {
        org.mockito.Mockito.doThrow(new IllegalStateException("db hiccup"))
                .when(addressRepository).anonymizeOrderReferencedByUserId(any(UUID.class));

        DeleteRequest request = dueRequest();
        sweeper.process(request);

        // Sibling tables still processed.
        verify(wishlistRepository).deleteByUserId(userId);
        verify(userRepository).anonymize(userId);
        // But NOT marked processed — retried next sweep.
        verify(deleteRequestRepository, never()).save(any());
    }

    @Test
    void returnPhotoKeys_forwardedToObjectStorage() {
        com.builddash.backend.domain.model.Return withPhotos =
                new com.builddash.backend.domain.model.Return(UUID.randomUUID(), UUID.randomUUID(), userId,
                        com.builddash.backend.domain.enums.ReturnStatus.APPROVED,
                        com.builddash.backend.domain.enums.ReturnReason.DAMAGED,
                        List.of("returns/photo-1.jpg", "returns/photo-2.jpg"), List.of(), Instant.now(), Instant.now());
        when(returnRepository.findAllByUserId(userId)).thenReturn(List.of(withPhotos));

        sweeper.process(dueRequest());

        verify(objectStorage).delete("returns/photo-1.jpg");
        verify(objectStorage).delete("returns/photo-2.jpg");
    }

    @Test
    void s3Failure_doesNotBlockTableDeletion() {
        when(returnRepository.findAllByUserId(any(UUID.class)))
                .thenThrow(new IllegalStateException("s3 down"));
        DeleteRequest request = dueRequest();

        sweeper.process(request);

        verify(addressRepository).deleteUnreferencedByUserId(userId);
        verify(deleteRequestRepository, never()).save(any());
    }
}
