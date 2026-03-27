package com.loopers.collector.coupon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 쿠폰 비동기 발급 요청 소비. 문서상 Consumer는 commerce-streamer에 둔다 ({@code KAFKA_APPLICATION.md} §0.2·§0.3).
 * API 프로세스와의 중복 소비 방지를 위해 전용 consumer group 을 사용한다.
 */
@Component
public class CouponIssueRequestCollectorListener {

    /** Outbox/릴레이가 전송하는 이벤트 타입과 동일해야 한다. */
    private static final String COUPON_ISSUE_REQUESTED = "COUPON_ISSUE_REQUESTED";

    private final CouponService couponService;
    private final ObjectMapper objectMapper;

    public CouponIssueRequestCollectorListener(CouponService couponService, ObjectMapper objectMapper) {
        this.couponService = couponService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${coupon.issue.topic-name:coupon-issue-requests}",
            groupId = "${coupon.issue.consumer-group:loopers-coupon-issue-consumer}"
    )
    public void onMessage(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        Envelope envelope = parse(record.value());
        if (COUPON_ISSUE_REQUESTED.equals(envelope.eventType())) {
            JsonNode data = envelope.data();
            Long userId = data.path("userId").asLong();
            Long couponTemplateId = data.path("couponTemplateId").asLong();
            if (data.hasNonNull("requestId")) {
                String requestId = data.get("requestId").asText();
                couponService.processCouponIssueRequest(requestId, userId, couponTemplateId);
            } else {
                couponService.issueIfAbsent(userId, couponTemplateId);
            }
        }
        acknowledgment.acknowledge();
    }

    private Envelope parse(Object rawValue) {
        try {
            byte[] bytes = rawValue instanceof byte[]
                    ? (byte[]) rawValue
                    : String.valueOf(rawValue).getBytes(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(bytes);
            if (node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            return objectMapper.treeToValue(node, Envelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid coupon issue request envelope", e);
        }
    }

    private record Envelope(
            String eventId,
            String eventType,
            JsonNode data
    ) {
    }
}
