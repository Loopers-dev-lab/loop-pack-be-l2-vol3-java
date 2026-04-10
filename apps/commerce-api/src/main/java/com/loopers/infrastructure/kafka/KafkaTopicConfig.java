package com.loopers.infrastructure.kafka;

import com.loopers.confg.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final int PARTITION_COUNT = 3;

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(KafkaTopics.CATALOG_EVENTS)
                .partitions(PARTITION_COUNT)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS)
                .partitions(PARTITION_COUNT)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic couponIssuedTopic() {
        return TopicBuilder.name(KafkaTopics.COUPON_ISSUED)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
