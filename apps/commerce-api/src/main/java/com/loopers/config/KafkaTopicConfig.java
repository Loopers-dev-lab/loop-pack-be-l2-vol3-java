package com.loopers.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Topic 설계
 *
 * 토픽 분리 기준:
 * 1. 발행 방식이 다르면 토픽 분리 (Outbox vs 직접 발행)
 * 2. Consumer 처리 로직이 다르면 토픽 분리
 * 3. 운영 모니터링 기준이 다르면 토픽 분리
 */
@Configuration
public class KafkaTopicConfig {

    /**
     * 상품 카탈로그 이벤트 — 좋아요/조회/판매량 집계
     * key=productId → 같은 상품의 이벤트를 순차 처리 → product_metrics upsert
     */
    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name("catalog-events-v1")
                .partitions(3)
                .replicas(1)  // 로컬 단일 Broker
                .build();
    }

    /**
     * 주문 이벤트 — 주문 확정 후 포인트 적립
     * key=orderId → 같은 주문의 이벤트를 순차 처리
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events-v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 쿠폰 발급 요청 (Command) — 선착순 쿠폰 발급
     * key=couponId → 같은 쿠폰의 요청을 순차 처리 → Lock 없이 동시성 제어
     */
    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name("coupon-issue-requests-v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 유저 행동 로깅 — 조회/클릭/좋아요/주문
     * key=userId → 유저별 행동 시간순 추적
     * Outbox 없이 직접 발행 (유실 허용)
     */
    @Bean
    public NewTopic userActivityEventsTopic() {
        return TopicBuilder.name("user-activity-events-v1")
                .partitions(3)
                .replicas(1)
                .config("retention.ms", String.valueOf(3L * 24 * 60 * 60 * 1000))  // 3일
                .build();
    }

    /**
     * DLQ — 처리 실패 메시지 격리
     */
    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name("pipeline-dlq-v1")
                .partitions(1)
                .replicas(1)
                .config("retention.ms", String.valueOf(30L * 24 * 60 * 60 * 1000))  // 30일
                .build();
    }
}
