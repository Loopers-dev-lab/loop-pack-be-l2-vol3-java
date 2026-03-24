package com.loopers.infrastructure.coupon;

import com.loopers.application.coupon.CouponEventPublisher;
import com.loopers.application.coupon.CouponIssueOutboxPayload;
import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaCouponEventPublisher implements CouponEventPublisher {

    private static final String TOPIC = "coupon-issue-requests";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Override
    public void publish(CouponIssueRequestedEvent event) {
        CouponIssueOutboxPayload payload = new CouponIssueOutboxPayload(
                event.eventId(), "CouponIssueRequested", 1,
                event.requestId(), event.couponTemplateId(), event.memberId(), event.requestedAt());
        kafkaTemplate.send(TOPIC, String.valueOf(event.couponTemplateId()), payload);
    }
}
