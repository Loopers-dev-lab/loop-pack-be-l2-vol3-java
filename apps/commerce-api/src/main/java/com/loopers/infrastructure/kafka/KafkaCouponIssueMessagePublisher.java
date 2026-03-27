package com.loopers.infrastructure.kafka;

import com.loopers.application.coupon.CouponIssueMessage;
import com.loopers.application.coupon.CouponIssueMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaCouponIssueMessagePublisher implements CouponIssueMessagePublisher {
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    private static final String TOPIC = "coupon-issue-requests";

    @Override
    public void publish(CouponIssueMessage message) {
        kafkaTemplate.send(TOPIC, String.valueOf(message.couponId()), message);
        log.info("쿠폰 발급 요청 Kafka 전송: requestId={}, couponId={}", message.requestId(), message.couponId());
    }
}
