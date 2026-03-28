package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.FcfsCouponIssueService;
import com.loopers.config.kafka.KafkaConfig;
import com.loopers.domain.event.CouponIssueRequestPayload;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
public class CouponIssueConsumer {

    private static final String DLQ_TOPIC = "coupon-issue-requests.dlq";

    private final FcfsCouponIssueService fcfsCouponIssueService;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @KafkaListener(
        topics = "coupon-issue-requests",
        containerFactory = KafkaConfig.SINGLE_LISTENER,
        groupId = "commerce-streamer-coupon"
    )
    public void consume(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            String rawValue = record.value() instanceof String
                ? (String) record.value()
                : objectMapper.writeValueAsString(record.value());

            JsonNode envelope = objectMapper.readTree(rawValue);
            if (envelope.isTextual()) {
                envelope = objectMapper.readTree(envelope.asText());
            }

            String eventId = requireText(envelope, "eventId");
            String eventType = requireText(envelope, "eventType");

            if (!"COUPON_ISSUE_REQUESTED".equals(eventType)) {
                log.warn("[CouponIssueConsumer] 알 수 없는 이벤트 타입: {}", eventType);
            } else if (eventHandledRepository.existsById(eventId)) {
                log.debug("[CouponIssueConsumer] 이미 처리된 이벤트 skip: eventId={}", eventId);
            } else {
                CouponIssueRequestPayload payload = objectMapper.treeToValue(
                    envelope.get("data"), CouponIssueRequestPayload.class);
                fcfsCouponIssueService.processIssueRequest(eventId, payload);
            }
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[CouponIssueConsumer] 처리 실패 → DLQ 전송: offset={}, error={}",
                record.offset(), e.getMessage(), e);
            try {
                sendToDlq(record);
                ack.acknowledge();
            } catch (Exception dlqException) {
                log.error("[CouponIssueConsumer] DLQ 전송 실패. 재배달 예정. error={}", dlqException.getMessage());
            }
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("필수 필드 누락: " + field);
        }
        return value.asText();
    }

    private void sendToDlq(ConsumerRecord<String, Object> record) {
        try {
            kafkaTemplate.send(DLQ_TOPIC, record.key(), record.value())
                .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(
                "[CouponIssueConsumer] DLQ 전송 실패: offset=" + record.offset(), e);
        }
    }
}
