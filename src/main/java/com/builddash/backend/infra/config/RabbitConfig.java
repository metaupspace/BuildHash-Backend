package com.builddash.backend.infra.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Generic AMQP infra, reused by every queue in the app (not just OTP dispatch) — JSON on the
 * wire instead of Java serialization, so messages stay human-readable and language-agnostic.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter("com.builddash.backend");
    }
}
