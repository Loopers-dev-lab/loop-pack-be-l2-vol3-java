package com.loopers.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String CATALOG_EVENTS = "catalog-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";

    /**
     * 카탈로그 이벤트 토픽 (좋아요, 조회수).
     * partitions=3: productId 기반 파티셔닝 → 같은 상품 이벤트는 같은 파티션.
     * replicas=1: 로컬 Kafka 브로커 1대.
     */
    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name(CATALOG_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 주문 이벤트 토픽 (주문 생성/취소/만료, 판매량).
     * partitions=3: orderId 기반 파티셔닝.
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 쿠폰 발급 요청 토픽.
     * partitions=1: 선착순 수량 제한을 위해 단일 파티션 필수 (순차 처리 보장).
     */
    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name(COUPON_ISSUE_REQUESTS)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
