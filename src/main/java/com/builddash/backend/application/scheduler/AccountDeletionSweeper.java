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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes due DPDP deletion requests per the locked per-table classification
 * (PLAN_PHASE8 architecture 5(d) — that table IS the audit artifact):
 *
 * RETAIN (untouched — tax/compliance, FKs NOT NULL): orders + line items, payments,
 * refunds, returns + line items, gst_notes, invoices + S3 invoice PDFs, idempotency_keys,
 * contract_pricing (B2B company data, not personal).
 *
 * HARD-DELETE: addresses, carts + items, coupon_redemptions, wishlist_entries,
 * notify_me_subscriptions, search_queries, devices, login_events, notification_logs,
 * delivery_slot_locks, support_tickets + messages — the last pair gated behind
 * account.deletion.support-tickets (OQ-9: HARD_DELETE default, RETAIN = one-config flip).
 *
 * ANONYMIZE: the users row — identity columns + blind-index columns nulled, row kept.
 *
 * CatalogOutboxRelay discipline: per-request and per-table try/catch; a failed table logs
 * and the rest proceed, but the request stays PENDING so the next sweep retries it — all
 * deletes are idempotent, so a partial run converges.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionSweeper {

    private final DeleteRequestRepository deleteRequestRepository;
    private final AccountDeletionProperties properties;
    private final ObjectStorage objectStorage;
    private final UserRepository userRepository;
    private final ReturnRepository returnRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final CartLineItemRepository cartLineItemRepository;
    private final WishlistRepository wishlistRepository;
    private final NotifyMeSubscriptionRepository notifyMeSubscriptionRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final SearchQueryLogRepository searchQueryLogRepository;
    private final DeviceRepository deviceRepository;
    private final LoginEventRepository loginEventRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final DeliverySlotLockRepository deliverySlotLockRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMessageRepository supportTicketMessageRepository;

    @Scheduled(cron = "${account.deletion.sweep-cron:0 30 2 * * *}")
    public void sweep() {
        for (DeleteRequest request : deleteRequestRepository.findDue(Instant.now())) {
            try {
                process(request);
            } catch (Exception e) {
                // Per-request isolation: one bad user never blocks the queue.
                log.error("Failed to process delete request {}", request.id(), e);
            }
        }
    }

    void process(DeleteRequest request) {
        UUID userId = request.userId();
        // Any table failure keeps the request PENDING (retried next sweep, idempotent).
        AtomicBoolean allOk = new AtomicBoolean(true);

        deleteReturnPhotos(userId, allOk);          // S3 first — returns rows are RETAINed
        // FK reality (orders.address_id NOT NULL): referenced addresses are anonymized in
        // place, unreferenced ones hard-deleted — confirmed with the product owner.
        hardDelete("addresses(anonymize-referenced)", userId,
                () -> addressRepository.anonymizeOrderReferencedByUserId(userId), allOk);
        hardDelete("addresses(delete-unreferenced)", userId,
                () -> addressRepository.deleteUnreferencedByUserId(userId), allOk);
        hardDelete("carts", userId, () -> deleteCarts(userId), allOk);
        hardDelete("coupon_redemptions", userId, () -> couponRedemptionRepository.deleteByUserId(userId), allOk);
        hardDelete("wishlist_entries", userId, () -> wishlistRepository.deleteByUserId(userId), allOk);
        hardDelete("notify_me_subscriptions", userId, () -> notifyMeSubscriptionRepository.deleteByUserId(userId), allOk);
        hardDelete("search_queries", userId, () -> searchQueryLogRepository.deleteByUserId(userId), allOk);
        hardDelete("devices", userId, () -> deviceRepository.deleteByUserId(userId), allOk);
        hardDelete("login_events", userId, () -> loginEventRepository.deleteByUserId(userId), allOk);
        hardDelete("notification_logs", userId, () -> notificationLogRepository.deleteByUserId(userId), allOk);
        hardDelete("delivery_slot_locks", userId, () -> deliverySlotLockRepository.deleteByUserId(userId), allOk);
        if (properties.getSupportTickets() == AccountDeletionProperties.SupportTicketDeletion.HARD_DELETE) {
            hardDelete("support_messages+tickets", userId, () -> deleteSupportThreads(userId), allOk);
        } else {
            log.info("support_tickets RETAINed for user {} per account.deletion.support-tickets", userId);
        }
        hardDelete("users(anonymize)", userId, () -> userRepository.anonymize(userId), allOk);

        if (allOk.get()) {
            deleteRequestRepository.save(request.markProcessed(Instant.now()));
            log.info("Delete request {} processed: user {} tombstoned", request.id(), userId);
        } else {
            log.warn("Delete request {} partially processed — staying PENDING for retry", request.id());
        }
    }

    private void deleteReturnPhotos(UUID userId, AtomicBoolean allOk) {
        try {
            returnRepository.findAllByUserId(userId).forEach(ret ->
                    ret.photoKeys().forEach(objectStorage::delete));
        } catch (Exception e) {
            allOk.set(false);
            log.error("Failed deleting S3 return photos for user {}", userId, e);
        }
    }

    private void deleteCarts(UUID userId) {
        cartRepository.findAllByUserId(userId).forEach(cart -> {
            cartLineItemRepository.deleteByCartId(cart.id());
            cartRepository.delete(cart.id());
        });
    }

    private void deleteSupportThreads(UUID userId) {
        // Per-ticket loop keeps FK order per thread: messages die before their ticket.
        supportTicketRepository.findByUserId(userId).forEach(ticket -> {
            supportTicketMessageRepository.deleteByTicketId(ticket.id());
            supportTicketRepository.deleteById(ticket.id());
        });
    }

    private void hardDelete(String table, UUID userId, Runnable delete, AtomicBoolean allOk) {
        try {
            delete.run();
        } catch (Exception e) {
            allOk.set(false);
            log.error("DPDP deletion failed for table {} user {}", table, userId, e);
        }
    }
}
