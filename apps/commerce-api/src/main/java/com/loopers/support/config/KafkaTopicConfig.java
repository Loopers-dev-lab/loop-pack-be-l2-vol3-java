package com.loopers.support.config;

import com.loopers.confg.kafka.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic catalogEvents() {
        return TopicBuilder.name(KafkaTopics.CATALOG_EVENTS).partitions(3).build();
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
    public NewTopic catalogEventsDlt() {
        return TopicBuilder.name(KafkaTopics.CATALOG_EVENTS + ".DLT").partitions(1).build();
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
