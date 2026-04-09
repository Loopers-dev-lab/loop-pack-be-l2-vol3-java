package com.loopers.support.config;

import com.loopers.confg.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic productViewEvents() {
        return TopicBuilder.name(KafkaTopics.PRODUCT_VIEW_EVENTS)
                .partitions(12)
                .config(TopicConfig.RETENTION_MS_CONFIG, "86400000")
                .build();
    }

    @Bean
    public NewTopic productInteractionEvents() {
        return TopicBuilder.name(KafkaTopics.PRODUCT_INTERACTION_EVENTS).partitions(3).build();
    }

    @Bean
    public NewTopic orderEvents() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS).partitions(3).build();
    }

    @Bean
    public NewTopic couponIssueRequests() {
        return TopicBuilder.name(KafkaTopics.COUPON_ISSUE_REQUESTS).partitions(3).build();
    }

    // DLT (Dead Letter Topic)

    @Bean
    public NewTopic productViewEventsDlt() {
        return TopicBuilder.name(KafkaTopics.PRODUCT_VIEW_EVENTS + ".DLT").partitions(1).build();
    }

    @Bean
    public NewTopic productInteractionEventsDlt() {
        return TopicBuilder.name(KafkaTopics.PRODUCT_INTERACTION_EVENTS + ".DLT").partitions(1).build();
    }

    @Bean
    public NewTopic orderEventsDlt() {
        return TopicBuilder.name(KafkaTopics.ORDER_EVENTS + ".DLT").partitions(1).build();
    }

    @Bean
    public NewTopic couponIssueRequestsDlt() {
        return TopicBuilder.name(KafkaTopics.COUPON_ISSUE_REQUESTS + ".DLT").partitions(1).build();
    }
}
