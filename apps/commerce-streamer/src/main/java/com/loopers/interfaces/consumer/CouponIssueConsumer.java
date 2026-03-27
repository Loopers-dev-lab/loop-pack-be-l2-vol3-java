package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.idempotent.IdempotentProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final IdempotentProcessor idempotentProcessor;
    private final CouponIssueProcessor couponIssueProcessor;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "coupon-issue-requests", groupId = "commerce-streamer")
    public void consume(ConsumerRecord<String, byte[]> record, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(record.value());
            String eventId = node.path("eventId").asText();
            String eventType = node.path("eventType").asText();
            JsonNode payload = objectMapper.readTree(node.path("payload").asText());

            Long couponId = payload.path("couponId").asLong();
            Long userId = payload.path("userId").asLong();

            idempotentProcessor.process(eventId, eventType,
                    () -> couponIssueProcessor.process(eventId, couponId, userId));

            ack.acknowledge();
        } catch (Exception e) {
            log.error("쿠폰 발급 이벤트 처리 실패: offset={}", record.offset(), e);
            throw new RuntimeException(e);
        }
    }
}
