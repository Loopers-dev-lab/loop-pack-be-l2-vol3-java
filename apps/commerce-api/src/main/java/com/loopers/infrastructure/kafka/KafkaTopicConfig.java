package com.loopers.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * 선착순 쿠폰 발급 요청 토픽.
     *
     * <p>파티션 수: 3 — partitionKey=couponId, 같은 쿠폰의 요청이 같은 파티션으로 라우팅되어 순서 보장.
     * Consumer concurrency=1 (SINGLE_LISTENER). 스케일아웃 시 concurrency를 파티션 수(3)까지 증가 가능.
     * 파티션 수 < Consumer 수 → 유휴 Consumer 발생하므로, Consumer 수는 파티션 수 이하로 유지.</p>
     */
    @Bean
    public NewTopic couponIssueRequestsTopic() {
        return TopicBuilder.name("coupon-issue-requests")
            .partitions(3)
            .replicas(1)
            .build();
    }

    /**
     * 카탈로그 이벤트 토픽 (좋아요, 조회수).
     *
     * <p>파티션 수: 3 — partitionKey=productId, 같은 상품의 이벤트가 같은 파티션으로 라우팅.
     * Consumer concurrency=3 (BATCH_LISTENER), 파티션 수와 concurrency 1:1 매칭.
     * 스케일아웃 시 파티션 수와 concurrency를 함께 증가시켜야 처리량이 선형 증가.</p>
     */
    @Bean
    public NewTopic catalogEventsTopic() {
        return TopicBuilder.name("catalog-events")
            .partitions(3)
            .replicas(1)
            .build();
    }

    /**
     * 주문 이벤트 토픽 (주문 생성, 주문 취소).
     *
     * <p>파티션 수: 3 — partitionKey=orderId, 같은 주문의 이벤트가 같은 파티션으로 라우팅되어 순서 보장.
     * Consumer concurrency=3 (BATCH_LISTENER), 파티션 수와 concurrency 1:1 매칭.
     * 스케일아웃 시 파티션 수와 concurrency를 함께 증가시켜야 처리량이 선형 증가.</p>
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
            .partitions(3)
            .replicas(1)
            .build();
    }
}
