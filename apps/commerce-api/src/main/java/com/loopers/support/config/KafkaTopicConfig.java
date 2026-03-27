package com.loopers.support.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic catalogEvents() {
        return TopicBuilder.name("catalog-events").partitions(3).build();
    }

    @Bean
    public NewTopic orderEvents() {
        return TopicBuilder.name("order-events").partitions(3).build();
    }

    @Bean
    public NewTopic couponIssueRequests() {
        return TopicBuilder.name("coupon-issue-requests").partitions(3).build();
    }

    // DLT (Dead Letter Topic)

    @Bean
    public NewTopic catalogEventsDlt() {
        return TopicBuilder.name("catalog-events.DLT").partitions(1).build();
    }

    @Bean
    public NewTopic orderEventsDlt() {
        return TopicBuilder.name("order-events.DLT").partitions(1).build();
    }

    @Bean
    public NewTopic couponIssueRequestsDlt() {
        return TopicBuilder.name("coupon-issue-requests.DLT").partitions(1).build();
    }
}
