package com.loopers.interfaces.consumer;

import com.loopers.application.coupon.CouponIssueConsumeApplicationService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.contract.coupon.CouponIssueRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueKafkaConsumer {

    private static final String CONSUMER_GROUP = "commerce-streamer-coupon-issue";

    private final CouponIssueConsumeApplicationService couponIssueConsumeApplicationService;

    @KafkaListener(
            topics = "${loopers.kafka.topic.coupon-issue-requested:commerce.coupon.issue-requested.v1}",
            containerFactory = KafkaConfig.BATCH_LISTENER,
            groupId = CONSUMER_GROUP
    )
    public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> message : messages) {
            if (message.value() instanceof CouponIssueRequestedEvent event) {
                couponIssueConsumeApplicationService.consume(CONSUMER_GROUP, event);
            } else {
                log.warn("Unsupported coupon issue message type. key={}, valueType={}",
                        message.key(),
                        message.value() == null ? "null" : message.value().getClass().getName());
            }
        }
        acknowledgment.acknowledge();
    }
}
