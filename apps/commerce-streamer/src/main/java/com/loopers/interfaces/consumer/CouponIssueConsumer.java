package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueService;
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

    private final CouponIssueService couponIssueService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC,
            groupId = "commerce-streamer-coupon",
            containerFactory = StreamerKafkaConfig.DLQ_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            CouponIssuePayload payload = parse(record);
            couponIssueService.processIssue(
                    payload.eventId(),
                    payload.requestId(),
                    payload.couponTemplateDbId(),
                    payload.memberId()
            );
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
