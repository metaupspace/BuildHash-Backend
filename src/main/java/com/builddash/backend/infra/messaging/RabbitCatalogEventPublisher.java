package com.builddash.backend.infra.messaging;

import com.builddash.backend.domain.port.CatalogEventPublisher;
import com.builddash.backend.infra.config.CatalogQueueConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class RabbitCatalogEventPublisher implements CatalogEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitCatalogEventPublisher.class);
    private static final long CONFIRM_TIMEOUT_MS = 5000;

    private final RabbitTemplate rabbitTemplate;

    public RabbitCatalogEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public boolean publishProductChanged(UUID correlationId, String payloadJson) {
        Message message = MessageBuilder.withBody(payloadJson.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .build();
        try {
            Boolean acked = rabbitTemplate.invoke(operations -> {
                operations.send(CatalogQueueConfig.QUEUE_NAME, message);
                return operations.waitForConfirms(CONFIRM_TIMEOUT_MS);
            });
            return Boolean.TRUE.equals(acked);
        } catch (Exception e) {
            log.warn("Publisher confirm failed for outbox event {}", correlationId, e);
            return false;
        }
    }
}
