package com.loopers.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Topic 설계 — 환경별 설정 외부화
 *
 * 토픽 분리 기준:
 * 1. 발행 방식이 다르면 토픽 분리 (Outbox vs 직접 발행)
 * 2. Consumer 처리 로직이 다르면 토픽 분리
 * 3. 운영 모니터링 기준이 다르면 토픽 분리
 *
 * 환경별 설정:
 * - application-local.yml: replicas=1, min-insync=1 (단일 브로커)
 * - application-prd.yml: replicas=3, min-insync=2 (3-브로커 클러스터)
 */
@Configuration
@EnableConfigurationProperties(TopicProperties.class)
public class KafkaTopicConfig {

    private final TopicProperties topicProperties;

    public KafkaTopicConfig(TopicProperties topicProperties) {
        this.topicProperties = topicProperties;
    }

    /**
     * 상품 카탈로그 이벤트 — 좋아요/조회/판매량 집계
     * key=productId → 같은 상품의 이벤트를 순차 처리 → product_metrics upsert
     */
    @Bean
    public NewTopic catalogEventsTopic() {
        var config = topicProperties.catalogEvents();
        return TopicBuilder.name(config.name())
                .partitions(config.partitions())
                .replicas(config.replicas())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(config.minInsyncReplicas()))
                .build();
    }

    /**
     * 주문 이벤트 — 주문 확정 후 포인트 적립
     * key=orderId → 같은 주문의 이벤트를 순차 처리
     */
    @Bean
    public NewTopic orderEventsTopic() {
        var config = topicProperties.orderEvents();
        return TopicBuilder.name(config.name())
                .partitions(config.partitions())
                .replicas(config.replicas())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(config.minInsyncReplicas()))
                .build();
    }

    /**
     * 쿠폰 발급 요청 (Command) — 선착순 쿠폰 발급
     * key=couponId → 같은 쿠폰의 요청을 순차 처리 → Lock 없이 동시성 제어
     */
    @Bean
    public NewTopic couponIssueRequestsTopic() {
        var config = topicProperties.couponIssueRequests();
        return TopicBuilder.name(config.name())
                .partitions(config.partitions())
                .replicas(config.replicas())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(config.minInsyncReplicas()))
                .build();
    }

    /**
     * 유저 행동 로깅 — 조회/클릭/좋아요/주문
     * key=userId → 유저별 행동 시간순 추적
     * Outbox 없이 직접 발행 (유실 허용)
     */
    @Bean
    public NewTopic userActivityEventsTopic() {
        var config = topicProperties.userActivityEvents();
        return TopicBuilder.name(config.name())
                .partitions(config.partitions())
                .replicas(config.replicas())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(config.minInsyncReplicas()))
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(config.retentionMs()))
                .build();
    }

    /**
     * DLQ — 처리 실패 메시지 격리
     */
    @Bean
    public NewTopic dlqTopic() {
        var config = topicProperties.pipelineDlq();
        return TopicBuilder.name(config.name())
                .partitions(config.partitions())
                .replicas(config.replicas())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(config.minInsyncReplicas()))
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(config.retentionMs()))
                .build();
    }
}
