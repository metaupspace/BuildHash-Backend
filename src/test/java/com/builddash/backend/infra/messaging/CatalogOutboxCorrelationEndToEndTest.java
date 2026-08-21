package com.builddash.backend.infra.messaging;

import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.CatalogOutboxEventRepository;
import com.builddash.backend.infra.config.CatalogQueueConfig;
import com.builddash.backend.infra.consumer.CatalogIndexedConfirmationListener;
import com.builddash.backend.infra.consumer.CatalogProductChangedListener;
import com.builddash.backend.support.RecordingSearchIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Wires the real publisher and both real listeners together in-process, mocking only the
 * broker transport (RabbitTemplate) at each hop — proves the x-outbox-event-id header
 * actually survives publish -> consume -> confirm -> markProcessed. Testing each class in
 * isolation (as the other tests in this checkpoint do) can't catch a header name typo or a
 * dropped header between hops; this test is what would.
 */
class CatalogOutboxCorrelationEndToEndTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void outboxEventId_survivesPublishConsumeConfirmMarkProcessed() throws Exception {
        UUID outboxEventId = UUID.randomUUID();
        ProductSyncPayload payload = new ProductSyncPayload(UUID.randomUUID(), "Product", "slug",
                "Cement", "BrandX", Map.of("weightKg", 50), "in_stock", 1_700_000_000_000L);
        String payloadJson = objectMapper.writeValueAsString(payload);

        // --- Hop 1: publish (RabbitCatalogEventPublisher) ---
        RabbitTemplate publishTemplate = mock(RabbitTemplate.class);
        RabbitOperations confirmedOperations = mock(RabbitOperations.class);
        when(confirmedOperations.waitForConfirms(anyLong())).thenReturn(true);
        when(publishTemplate.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInRabbit(confirmedOperations);
        });

        RabbitCatalogEventPublisher publisher = new RabbitCatalogEventPublisher(publishTemplate);
        boolean acked = publisher.publishProductChanged(outboxEventId, payloadJson);
        assertThat(acked).isTrue();

        ArgumentCaptor<Message> sentToChangedQueue = ArgumentCaptor.forClass(Message.class);
        verify(confirmedOperations).send(eq(CatalogQueueConfig.QUEUE_NAME), sentToChangedQueue.capture());
        Message changedMessage = sentToChangedQueue.getValue();

        // --- Hop 2: consume + upsert + publish confirmation (CatalogProductChangedListener) ---
        RecordingSearchIndex searchIndex = new RecordingSearchIndex();
        RabbitTemplate confirmationTemplate = mock(RabbitTemplate.class);
        CatalogProductChangedListener changedListener =
                new CatalogProductChangedListener(searchIndex, objectMapper, confirmationTemplate);

        changedListener.onMessage(changedMessage);

        ArgumentCaptor<Message> sentToIndexedQueue = ArgumentCaptor.forClass(Message.class);
        verify(confirmationTemplate).send(eq(CatalogQueueConfig.INDEXED_QUEUE_NAME), sentToIndexedQueue.capture());
        Message confirmationMessage = sentToIndexedQueue.getValue();

        // --- Hop 3: consume confirmation + markProcessed (CatalogIndexedConfirmationListener) ---
        CatalogOutboxEventRepository catalogOutboxEventRepository = mock(CatalogOutboxEventRepository.class);
        CatalogIndexedConfirmationListener confirmationListener =
                new CatalogIndexedConfirmationListener(catalogOutboxEventRepository);

        confirmationListener.onMessage(confirmationMessage);

        verify(catalogOutboxEventRepository).markProcessed(outboxEventId);
    }
}
