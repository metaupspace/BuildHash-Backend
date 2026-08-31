package com.builddash.backend.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DPDP export document (PLAN_PHASE8 decision 8): one section per table of the §1d
 * user-data inventory, domain records serialized as-is. Sparse users get empty lists —
 * every section is ALWAYS present, never missing.
 */
public record UserDataExport(
        Instant generatedAt,
        UUID userId,
        User profile,
        List<Device> devices,
        List<LoginEvent> loginEvents,
        List<Address> addresses,
        List<CartSection> carts,
        List<OrderSection> orders,
        List<ReturnSection> returns,
        List<Review> reviews,
        List<Question> questions,
        List<Answer> answers,
        List<WishlistEntry> wishlistEntries,
        List<NotifyMeSubscription> notifyMeSubscriptions,
        List<SearchQueryLogEntry> searchQueries,
        List<CouponRedemption> couponRedemptions,
        List<ContractPrice> contractPrices,
        List<NotificationLog> notificationLogs,
        List<SupportTicketSection> supportTickets
) {

    public record CartSection(Cart cart, List<CartLineItem> items) {
    }

    /** lineItems travel inside the Order record; payments and invoice are per-order children. */
    public record OrderSection(Order order, List<Payment> payments, Invoice invoice) {
    }

    public record ReturnSection(Return returnRecord, List<Refund> refunds, List<GstNote> gstNotes) {
    }

    public record SupportTicketSection(SupportTicket ticket, List<SupportTicketMessage> messages) {
    }
}
