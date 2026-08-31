package com.builddash.backend.application.impl;

import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.User;
import com.builddash.backend.domain.model.UserDataExport;
import com.builddash.backend.domain.port.AddressRepository;
import com.builddash.backend.domain.port.AnswerRepository;
import com.builddash.backend.domain.port.CartLineItemRepository;
import com.builddash.backend.domain.port.CartRepository;
import com.builddash.backend.domain.port.ContractPriceRepository;
import com.builddash.backend.domain.port.CouponRedemptionRepository;
import com.builddash.backend.domain.port.DeviceRepository;
import com.builddash.backend.domain.port.InvoiceRepository;
import com.builddash.backend.domain.port.LoginEventRepository;
import com.builddash.backend.domain.port.NotificationLogRepository;
import com.builddash.backend.domain.port.NotifyMeSubscriptionRepository;
import com.builddash.backend.domain.port.OrderRepository;
import com.builddash.backend.domain.port.PaymentRepository;
import com.builddash.backend.domain.port.QuestionRepository;
import com.builddash.backend.domain.port.RefundRepository;
import com.builddash.backend.domain.port.ReturnRepository;
import com.builddash.backend.domain.port.ReviewRepository;
import com.builddash.backend.domain.port.GstNoteRepository;
import com.builddash.backend.domain.port.SearchQueryLogRepository;
import com.builddash.backend.domain.port.SupportTicketMessageRepository;
import com.builddash.backend.domain.port.SupportTicketRepository;
import com.builddash.backend.domain.port.UserDataExporter;
import com.builddash.backend.domain.port.UserRepository;
import com.builddash.backend.domain.port.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DPDP export assembler (PLAN_PHASE8 decision 8, architecture 5(c)): repository-only access,
 * no raw SQL — every section reads through an existing port. One section per table of the
 * §1d inventory; nested children (cart items, payments, refunds, gst notes, ticket messages)
 * travel inside their parent's section.
 */
@Component
@RequiredArgsConstructor
public class UserDataExporterImpl implements UserDataExporter {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final LoginEventRepository loginEventRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final CartLineItemRepository cartLineItemRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReturnRepository returnRepository;
    private final RefundRepository refundRepository;
    private final GstNoteRepository gstNoteRepository;
    private final ReviewRepository reviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final WishlistRepository wishlistRepository;
    private final NotifyMeSubscriptionRepository notifyMeSubscriptionRepository;
    private final SearchQueryLogRepository searchQueryLogRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final ContractPriceRepository contractPriceRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final SupportTicketMessageRepository supportTicketMessageRepository;

    @Override
    public UserDataExport export(UUID userId) {
        User profile = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        List<UserDataExport.CartSection> carts = cartRepository.findAllByUserId(userId).stream()
                .map(cart -> new UserDataExport.CartSection(cart, cartLineItemRepository.findByCartId(cart.id())))
                .toList();

        List<UserDataExport.OrderSection> orders = orderRepository.findAllByUserId(userId).stream()
                .map(order -> new UserDataExport.OrderSection(
                        order,
                        paymentRepository.findAllByOrderId(order.id()),
                        invoiceRepository.findByOrderId(order.id()).orElse(null)))
                .toList();

        List<UserDataExport.ReturnSection> returns = returnRepository.findAllByUserId(userId).stream()
                .map(ret -> new UserDataExport.ReturnSection(
                        ret,
                        refundRepository.findAllByReturnId(ret.id()),
                        gstNoteRepository.findAllByReturnId(ret.id())))
                .toList();

        List<UserDataExport.SupportTicketSection> tickets = supportTicketRepository.findByUserId(userId).stream()
                .map(ticket -> new UserDataExport.SupportTicketSection(
                        ticket, supportTicketMessageRepository.findByTicketId(ticket.id())))
                .toList();

        // delivery_slot_locks is deliberately absent from the export: locks are transient,
        // seconds-lived concurrency guards, not user data warranting a DPDP export section.

        return new UserDataExport(
                Instant.now(), userId, profile,
                deviceRepository.findAllByUserId(userId),
                loginEventRepository.findByUserIdOrderByCreatedAtDesc(userId),
                addressRepository.findByUserId(userId),
                carts, orders, returns,
                reviewRepository.findAllByUserId(userId),
                questionRepository.findAllByUserId(userId),
                answerRepository.findAllByUserId(userId),
                wishlistRepository.findByUserId(userId),
                notifyMeSubscriptionRepository.findAllByUserId(userId),
                searchQueryLogRepository.findByUserId(userId, Integer.MAX_VALUE),
                couponRedemptionRepository.findAllByUserId(userId),
                contractPriceRepository.findAllByUserId(userId),
                notificationLogRepository.findAllByUserId(userId),
                tickets
        );
    }
}
