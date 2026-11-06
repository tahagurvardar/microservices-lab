package com.example.client.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class ClientEventTopicConfiguration {
    @Bean
    NewTopic clientEventsTopic(@Value("${app.kafka.topic:client.events}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
