package com.loopers.config;

import com.loopers.infrastructure.outbox.OutboxTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽 자동 생성 설정.
 *
 * KafkaAdmin이 애플리케이션 시작 시 토픽이 없으면 생성한다.
 * 파티션 3개: 같은 키(productId, orderId)는 같은 파티션 → 순서 보장
 * Replication factor 1: 로컬 단일 브로커 환경
 */
@Configuration
public class KafkaTopicConfig {

    private static final int PARTITION_COUNT = 3;
    private static final int REPLICATION_FACTOR = 1;

    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(OutboxTopics.CATALOG_EVENTS)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(OutboxTopics.ORDER_EVENTS)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    // 선착순 쿠폰 발급 요청 — templateId 파티션 키로 같은 쿠폰 순차 처리 보장
    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name(OutboxTopics.COUPON_ISSUE_REQUESTS)
                .partitions(PARTITION_COUNT)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    // DLQ 토픽 — 컨슈머 처리 실패 메시지 보관 (파티션 1개: 순서 무관, 수동 확인용)
    @Bean
    public NewTopic catalogEventsDlqTopic() {
        return TopicBuilder.name(OutboxTopics.CATALOG_EVENTS + ".dlq")
                .partitions(1)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic orderEventsDlqTopic() {
        return TopicBuilder.name(OutboxTopics.ORDER_EVENTS + ".dlq")
                .partitions(1)
                .replicas(REPLICATION_FACTOR)
                .build();
    }

    @Bean
    public NewTopic couponIssueRequestsDlqTopic() {
        return TopicBuilder.name(OutboxTopics.COUPON_ISSUE_REQUESTS + ".dlq")
                .partitions(1)
                .replicas(REPLICATION_FACTOR)
                .build();
    }
}
