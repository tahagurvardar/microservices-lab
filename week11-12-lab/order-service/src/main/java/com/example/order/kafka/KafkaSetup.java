package com.example.order.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RoundRobinPartitioner;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.admin.NewTopic;

@Configuration
public class KafkaSetup {
    @Bean
    NewTopic orderTopic() {
        return TopicBuilder.name("orders").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic rawCrashTopic() {
        return TopicBuilder.name("orders-crash-raw").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic safeCrashTopic() {
        return TopicBuilder.name("orders-crash-safe").partitions(1).replicas(1).build();
    }

    private Map<String, Object> producerSettings(String brokers) {
        Map<String, Object> settings = new HashMap<>();
        settings.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        settings.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        settings.put(ProducerConfig.ACKS_CONFIG, "all");
        settings.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        settings.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        return settings;
    }

    @Bean("keyedKafkaTemplate")
    KafkaTemplate<String, String> keyedKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String brokers) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerSettings(brokers)));
    }

    @Bean("roundRobinKafkaTemplate")
    KafkaTemplate<String, String> roundRobinKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String brokers) {
        Map<String, Object> settings = producerSettings(brokers);
        settings.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, RoundRobinPartitioner.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(settings));
    }
}
