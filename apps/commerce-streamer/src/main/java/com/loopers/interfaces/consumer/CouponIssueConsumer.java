package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueProcessor;
import com.loopers.application.idempotent.IdempotentProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.infrastructure.outbox.OutboxMarkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final IdempotentProcessor idempotentProcessor;
    private final CouponIssueProcessor couponIssueProcessor;
    private final OutboxMarkRepository outboxMarkRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.COUPON_ISSUE_REQUESTS, groupId = "coupon-processing",
            containerFactory = KafkaConfig.BATCH_LISTENER)
    public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
        for (int i = 0; i < records.size(); i++) {
            try {
                processRecord(records.get(i));
            } catch (Exception e) {
                throw new BatchListenerFailedException("쿠폰 발급 이벤트 처리 실패", e, i);
            }
        }
        ack.acknowledge();
    }

    private static final String TOPIC = KafkaTopics.COUPON_ISSUE_REQUESTS;
    private static final String GROUP_ID = "coupon-processing";

    private void processRecord(ConsumerRecord<String, byte[]> record) throws Exception {
        JsonNode node = objectMapper.readTree(record.value());
        String eventId = node.path("eventId").asText();
        String eventType = node.path("eventType").asText();
        JsonNode payload = objectMapper.readTree(node.path("payload").asText());

        Long couponId = payload.path("couponId").asLong();
        Long userId = payload.path("userId").asLong();
        String idempotencyKey = GROUP_ID + ":" + eventId;

        idempotentProcessor.process(idempotencyKey, eventType, TOPIC, GROUP_ID,
                () -> couponIssueProcessor.process(eventId, couponId, userId));

        outboxMarkRepository.markPublished(eventId);
    }
}
