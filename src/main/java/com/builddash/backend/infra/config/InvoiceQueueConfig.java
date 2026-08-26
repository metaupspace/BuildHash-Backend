package com.builddash.backend.infra.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InvoiceQueueConfig {

    public static final String DLQ_NAME = "invoice.generation.dlq";
    public static final String DLX_NAME = "invoice.generation.dlx";

    @Bean
    public DirectExchange invoiceGenerationDlx() {
        return new DirectExchange(DLX_NAME);
    }

    @Bean
    public Queue invoiceGenerationDlq() {
        return new Queue(DLQ_NAME, true);
    }

    @Bean
    public Binding invoiceGenerationDlqBinding(Queue invoiceGenerationDlq, DirectExchange invoiceGenerationDlx) {
        return BindingBuilder.bind(invoiceGenerationDlq).to(invoiceGenerationDlx).with(DLQ_NAME);
    }
}
