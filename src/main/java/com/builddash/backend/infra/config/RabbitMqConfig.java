package com.builddash.backend.infra.config;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter("com.builddash.backend") {
            @Override
            public Object fromMessage(Message message) throws MessageConversionException {
                if (message.getBody() == null || message.getBody().length == 0) {
                    return new byte[0];
                }
                return super.fromMessage(message);
            }
        };
    }
}
