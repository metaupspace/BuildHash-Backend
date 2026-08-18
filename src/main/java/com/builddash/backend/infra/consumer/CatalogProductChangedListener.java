package com.builddash.backend.infra.consumer;

import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.domain.port.SearchIndex;
import com.builddash.backend.infra.config.CatalogQueueConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads the raw message body directly (bypasses the generic Jackson2JsonMessageConverter,
 * matching how RabbitCatalogEventPublisher publishes raw bytes) — explicit, no reliance on
 * converter type inference. A parse failure or upsert failure is deliberately left to
 * propagate: that's what lets Spring's configured retry-then-reject engage the queue's DLQ
 * (CatalogQueueConfig) instead of the message silently vanishing.
 */
@Component
public class CatalogProductChangedListener {

    private final SearchIndex searchIndex;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    public CatalogProductChangedListener(SearchIndex searchIndex, ObjectMapper objectMapper, RabbitTemplate rabbitTemplate) {
        this.searchIndex = searchIndex;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = CatalogQueueConfig.QUEUE_NAME)
    public void onMessage(Message message) throws IOException {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        ProductSyncPayload payload = objectMapper.readValue(json, ProductSyncPayload.class);

        searchIndex.upsertProduct(payload);

        publishIndexedConfirmation(message);
    }

    private void publishIndexedConfirmation(Message original) {
        Object outboxEventId = original.getMessageProperties().getHeaders().get(CatalogQueueConfig.OUTBOX_EVENT_ID_HEADER);
        Message confirmation = MessageBuilder.withBody(new byte[0])
                .setHeader(CatalogQueueConfig.OUTBOX_EVENT_ID_HEADER, outboxEventId)
                .build();
        rabbitTemplate.send(CatalogQueueConfig.INDEXED_QUEUE_NAME, confirmation);
    }
}
