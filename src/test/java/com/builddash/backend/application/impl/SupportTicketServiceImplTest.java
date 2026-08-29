package com.builddash.backend.application.impl;

import com.builddash.backend.domain.enums.SupportTicketCategory;
import com.builddash.backend.domain.enums.SupportTicketMessageSender;
import com.builddash.backend.domain.enums.SupportTicketStatus;
import com.builddash.backend.domain.exception.InvalidSupportTicketStateException;
import com.builddash.backend.domain.exception.NotFoundException;
import com.builddash.backend.domain.model.SupportTicket;
import com.builddash.backend.domain.model.SupportTicketMessage;
import com.builddash.backend.domain.port.SupportTicketMessageRepository;
import com.builddash.backend.domain.port.SupportTicketRepository;
import com.builddash.backend.infra.config.SupportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceImplTest {

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private SupportTicketMessageRepository messageRepository;

    private SupportProperties supportProperties;

    private SupportTicketServiceImpl service;

    private final UUID ownerUserId = UUID.randomUUID();
    private final UUID strangerUserId = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        supportProperties = new SupportProperties();
        service = new SupportTicketServiceImpl(ticketRepository, messageRepository, supportProperties);
    }

    private SupportTicket openTicket() {
        return new SupportTicket(ticketId, ownerUserId, SupportTicketCategory.ORDER_ISSUE,
                SupportTicketStatus.OPEN, "subject", Instant.now().plus(Duration.ofHours(24)),
                Instant.now(), Instant.now());
    }

    @Test
    void createTicket_computesSlaDueAtFromCategoryDefaultAndWritesFirstMessageAtomically() {
        Instant before = Instant.now();
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SupportTicket created = service.createTicket(ownerUserId, SupportTicketCategory.PAYMENT_ISSUE, "pay issue", "help");

        ArgumentCaptor<SupportTicket> ticketCaptor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().status()).isEqualTo(SupportTicketStatus.OPEN);
        // PAYMENT_ISSUE code default = 4h
        assertThat(created.slaDueAt()).isBetween(before.plus(Duration.ofHours(4)).minusSeconds(5),
                Instant.now().plus(Duration.ofHours(4)).plusSeconds(5));

        ArgumentCaptor<SupportTicketMessage> messageCaptor = ArgumentCaptor.forClass(SupportTicketMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().senderRole()).isEqualTo(SupportTicketMessageSender.CUSTOMER);
        assertThat(messageCaptor.getValue().body()).isEqualTo("help");
        assertThat(messageCaptor.getValue().ticketId()).isEqualTo(created.id());
    }

    @Test
    void createTicket_yamlOverriddenCategoryTakesPrecedenceOverCodeDefault() {
        supportProperties.getSla().put("PRODUCT_QUERY", Duration.ofHours(1));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SupportTicket created = service.createTicket(ownerUserId, SupportTicketCategory.PRODUCT_QUERY, "s", "m");

        // Code default 48h, overridden to 1h
        assertThat(created.slaDueAt()).isBefore(Instant.now().plus(Duration.ofHours(2)));
    }

    @Test
    void getTicket_nonOwnerWithoutElevatedRole_gets404() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(openTicket()));

        assertThatThrownBy(() -> service.getTicket(strangerUserId, List.of("USER"), ticketId))
                .isInstanceOf(NotFoundException.class);

        // Elevated roles read the same ticket fine
        assertThat(service.getTicket(strangerUserId, List.of("VENDOR"), ticketId).id()).isEqualTo(ticketId);
        assertThat(service.getTicket(strangerUserId, List.of("ADMIN"), ticketId).id()).isEqualTo(ticketId);
    }

    @Test
    void listMessages_nonOwnerRejected_beforeAnyMessageIsRead() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(openTicket()));

        assertThatThrownBy(() -> service.listMessages(strangerUserId, List.of("USER"), ticketId))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(messageRepository);
    }

    @Test
    void appendMessage_nonOwnerRejected_beforeAnyMessageRowIsWritten() {
        // The named ordering proof: authorization happens BEFORE the write — nothing is
        // written-then-hidden.
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(openTicket()));

        assertThatThrownBy(() -> service.appendMessage(strangerUserId, List.of("USER"), ticketId, "sneak"))
                .isInstanceOf(NotFoundException.class);

        verify(messageRepository, never()).save(any());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void appendMessage_ownerSendsAsCustomer_agentSendsAsAgent() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(openTicket()));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SupportTicketMessage ownerMessage = service.appendMessage(ownerUserId, List.of("USER"), ticketId, "mine");
        assertThat(ownerMessage.senderRole()).isEqualTo(SupportTicketMessageSender.CUSTOMER);

        SupportTicketMessage agentMessage = service.appendMessage(UUID.randomUUID(), List.of("VENDOR"), ticketId, "agent reply");
        assertThat(agentMessage.senderRole()).isEqualTo(SupportTicketMessageSender.AGENT);
    }

    @Test
    void escalate_openTicketTransitions_nonOpenThrows() {
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(openTicket()));
        when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.escalate(ticketId).status()).isEqualTo(SupportTicketStatus.ESCALATED);

        SupportTicket escalated = new SupportTicket(ticketId, ownerUserId, SupportTicketCategory.OTHER,
                SupportTicketStatus.ESCALATED, "s", Instant.now(), Instant.now(), Instant.now());
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(escalated));
        assertThatThrownBy(() -> service.escalate(ticketId))
                .isInstanceOf(InvalidSupportTicketStateException.class);
    }
}
