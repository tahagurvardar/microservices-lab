package com.example.email.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class NotificationRabbitConfiguration {
    public static final String EXCHANGE = "email.notifications.exchange";
    public static final String ROUTING_KEY = "email.notification";
    public static final String QUEUE = "email.notifications.queue";
    public static final String DEAD_EXCHANGE = "email.notifications.dead.exchange";
    public static final String DEAD_ROUTING_KEY = "email.notification.dead";
    public static final String DEAD_QUEUE = "email.notifications.dead";

    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey(DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding notificationBinding(@Qualifier("notificationQueue") Queue notificationQueue,
                                @Qualifier("notificationExchange") DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(ROUTING_KEY);
    }

    @Bean
    DirectExchange notificationDeadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationDeadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    Binding notificationDeadBinding(@Qualifier("notificationDeadQueue") Queue notificationDeadQueue,
                                    @Qualifier("notificationDeadExchange") DirectExchange notificationDeadExchange) {
        return BindingBuilder.bind(notificationDeadQueue)
                .to(notificationDeadExchange).with(DEAD_ROUTING_KEY);
    }
}
