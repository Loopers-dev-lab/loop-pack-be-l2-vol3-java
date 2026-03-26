package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueApp;
import com.loopers.infrastructure.kafka.StreamerKafkaConfig;
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
public class CouponIssueConsumer {

    private static final String TOPIC = "coupon-issue-requests";

    private final CouponIssueApp couponIssueApp;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC,
            groupId = "commerce-streamer-coupon",
            containerFactory = StreamerKafkaConfig.DLQ_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<Object, Object> record = records.get(i);
            try {
                CouponIssuePayload payload = parse(record);
                couponIssueApp.processIssue(
                        payload.eventId(),
                        payload.requestId(),
                        payload.couponTemplateDbId(),
                        payload.memberId()
                );
            } catch (Exception e) {
                log.error("[COUPON_ISSUE_FAILED] offset={}, key={}", record.offset(), record.key(), e);
                throw new org.springframework.kafka.listener.BatchListenerFailedException(
                        "coupon-issue-requests processing failed at index " + i, e, i);
            }
        }
        acknowledgment.acknowledge();
    }

    private CouponIssuePayload parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), CouponIssuePayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize CouponIssuePayload", e);
        }
    }
}
