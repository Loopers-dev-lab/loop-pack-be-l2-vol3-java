package com.loopers.infrastructure.coupon;

import com.loopers.application.coupon.event.CouponIssuedMessage;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final CouponService couponService;

    @KafkaListener(
            topics = KafkaTopics.COUPON_ISSUED,
            groupId = "coupon-issue-consumer",
            containerFactory = KafkaConfig.SINGLE_LISTENER)
    public void consume(@Payload CouponIssuedMessage message, Acknowledgment ack) {
        try {
            couponService.issue(message.couponId(), message.userId());
            ack.acknowledge();
        } catch (DataIntegrityViolationException e) {
            log.warn("중복 발급 메시지 skip: couponId={}, userId={}",
                    message.couponId(), message.userId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("쿠폰 발급 실패, 재시도 예정: couponId={}, userId={}",
                    message.couponId(), message.userId(), e);
            throw e;
        }
    }
}
