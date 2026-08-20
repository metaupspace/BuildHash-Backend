package com.builddash.backend.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * catalog.product.changed gets its own DLQ from day one — unlike Phase 0's OTP dispatch queue,
 * a lost catalog-changed message means a product silently never reaches search (invisible
 * failure), so redelivery gives up and lands in the DLQ instead of retrying forever.
 */
@Configuration
public class CatalogQueueConfig {

    public static final String QUEUE_NAME = "catalog.product.changed";
    public static final String DLQ_NAME = "catalog.product.changed.dlq";
    private static final String DLX_NAME = "catalog.product.changed.dlx";

    @Bean
    public DirectExchange catalogProductChangedDlx() {
        return new DirectExchange(DLX_NAME);
    }

    @Bean
    public Queue catalogProductChangedDlq() {
        return new Queue(DLQ_NAME, true);
    }

    @Bean
    public Binding catalogProductChangedDlqBinding(Queue catalogProductChangedDlq, DirectExchange catalogProductChangedDlx) {
        return BindingBuilder.bind(catalogProductChangedDlq).to(catalogProductChangedDlx).with(DLQ_NAME);
    }

    @Bean
    public Queue catalogProductChangedQueue() {
        return QueueBuilder.durable(QUEUE_NAME)
                .withArgument("x-dead-letter-exchange", DLX_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_NAME)
                .build();
    }
}
