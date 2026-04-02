package com.loopers.config;

import com.loopers.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽 설정.
 */
@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(KafkaTopics.CATALOG_EVENTS)
            .partitions(PARTITIONS)
            .replicas(REPLICAS)
            .build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS)
            .partitions(PARTITIONS)
            .replicas(REPLICAS)
            .build();
    }

    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name(KafkaTopics.COUPON_ISSUE_REQUESTS)
            .partitions(PARTITIONS)
            .replicas(REPLICAS)
            .build();
    }
}
