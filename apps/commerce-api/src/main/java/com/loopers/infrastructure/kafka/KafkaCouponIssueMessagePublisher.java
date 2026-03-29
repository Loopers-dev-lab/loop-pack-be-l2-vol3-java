package com.loopers.infrastructure.kafka;

import com.loopers.application.coupon.CouponIssueMessage;
import com.loopers.application.coupon.CouponIssueMessagePublisher;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaCouponIssueMessagePublisher implements CouponIssueMessagePublisher {
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    private static final String TOPIC = "coupon-issue-requests";

    @Override
    public void publish(CouponIssueMessage message) {
        try {
            kafkaTemplate.send(TOPIC, String.valueOf(message.couponId()), message)
                    .get(5, TimeUnit.SECONDS);
            log.info("쿠폰 발급 요청 Kafka 전송: requestId={}, couponId={}", message.requestId(), message.couponId());
        } catch (Exception e) {
            log.error("쿠폰 발급 요청 Kafka 전송 실패: requestId={}", message.requestId(), e);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청 전송에 실패했습니다.");
        }
    }
}
