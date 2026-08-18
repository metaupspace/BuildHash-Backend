package com.builddash.backend.infra.consumer;

import com.builddash.backend.domain.model.ProductSyncPayload;
import com.builddash.backend.infra.config.CatalogQueueConfig;
import com.builddash.backend.support.RecordingSearchIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CatalogProductChangedListenerTest {

    private final RecordingSearchIndex searchIndex = new RecordingSearchIndex();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final CatalogProductChangedListener listener =
            new CatalogProductChangedListener(searchIndex, objectMapper, rabbitTemplate);

    private ProductSyncPayload payload(UUID productId, String name, long updatedAtEpochMillis) {
        return new ProductSyncPayload(productId, name, "slug", "Cement", "BrandX",
                Map.of("weightKg", 50), "in_stock", updatedAtEpochMillis);
    }

    private Message toMessage(ProductSyncPayload payload) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        return MessageBuilder.withBody(body)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader(CatalogQueueConfig.OUTBOX_EVENT_ID_HEADER, UUID.randomUUID().toString())
                .build();
    }

    @Test
    void onMessage_outOfOrderDelivery_higherVersionWins() throws IOException {
        UUID productId = UUID.randomUUID();
        ProductSyncPayload v2 = payload(productId, "New Name", 2000L);
        ProductSyncPayload v1 = payload(productId, "Old Name", 1000L);

        listener.onMessage(toMessage(v2));
        listener.onMessage(toMessage(v1));

        assertThat(searchIndex.get(productId).name()).isEqualTo("New Name");
        assertThat(searchIndex.get(productId).updatedAtEpochMillis()).isEqualTo(2000L);
    }

    @Test
    void onMessage_malformedPayload_throwsRatherThanSwallowing() {
        Message malformed = MessageBuilder.withBody("not json".getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader(CatalogQueueConfig.OUTBOX_EVENT_ID_HEADER, UUID.randomUUID().toString())
                .build();

        assertThatThrownBy(() -> listener.onMessage(malformed)).isInstanceOf(IOException.class);
    }
}
