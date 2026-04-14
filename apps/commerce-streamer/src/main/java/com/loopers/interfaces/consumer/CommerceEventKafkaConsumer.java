package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.collector.CouponIssueConsumeService;
import com.loopers.application.collector.EventDedupService;
import com.loopers.application.collector.ProductMetricsAggregationService;
import com.loopers.application.collector.RealtimeRankingAggregationService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.kafka.message.KafkaEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommerceEventKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final EventDedupService eventDedupService;
    private final ProductMetricsAggregationService productMetricsAggregationService;
    private final RealtimeRankingAggregationService realtimeRankingAggregationService;
    private final CouponIssueConsumeService couponIssueConsumeService;

    @Value("${commerce.consumer.group.metrics}")
    private String metricsConsumerGroup;

    @Value("${commerce.consumer.group.coupon}")
    private String couponConsumerGroup;

    @KafkaListener(
        topics = {"${commerce.kafka.topics.catalog-events}", "${commerce.kafka.topics.order-events}"},
        groupId = "${commerce.consumer.group.metrics}",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    @Transactional
    public void consumeMetricsEvents(
        List<ConsumerRecord<Object, Object>> messages,
        Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<Object, Object> message : messages) {
            processMetricsRecord(message);
        }
        acknowledgment.acknowledge();
    }

    @KafkaListener(
        topics = {"${commerce.kafka.topics.coupon-issue-requests}"},
        groupId = "${commerce.consumer.group.coupon}",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    @Transactional
    public void consumeCouponIssueRequests(
        List<ConsumerRecord<Object, Object>> messages,
        Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<Object, Object> message : messages) {
            processCouponRecord(message);
        }
        acknowledgment.acknowledge();
    }

    protected void processMetricsRecord(ConsumerRecord<Object, Object> message) {
        KafkaEventEnvelope event = parse(message.value());
        if (!eventDedupService.markIfNotHandled(event.eventId(), metricsConsumerGroup)) {
            return;
        }

        switch (event.eventType()) {
            case "PRODUCT_LIKE_CHANGED" -> handleLikeChanged(event);
            case "PRODUCT_VIEWED" -> handleProductViewed(event);
            case "PRODUCT_DWELLED" -> handleProductDwelled(event);
            case "ORDER_PLACED" -> handleOrderPlaced(event);
            default -> log.debug("Skip unknown metrics event type: {}", event.eventType());
        }
    }

    protected void processCouponRecord(ConsumerRecord<Object, Object> message) {
        KafkaEventEnvelope event = parse(message.value());
        if (!eventDedupService.markIfNotHandled(event.eventId(), couponConsumerGroup)) {
            return;
        }

        if (!"COUPON_ISSUE_REQUESTED".equals(event.eventType())) {
            return;
        }

        Long requestId = toLong(event.payload().get("requestId"));
        Long couponId = toLong(event.payload().get("couponId"));
        Long userId = toLong(event.payload().get("userId"));
        couponIssueConsumeService.handleRequest(requestId, couponId, userId);
    }

    private void handleLikeChanged(KafkaEventEnvelope event) {
        Long productId = toLong(event.payload().get("productId"));
        long delta = toLong(event.payload().get("delta"));
        productMetricsAggregationService.applyLikeDelta(productId, delta, event.occurredAt());
        realtimeRankingAggregationService.applyLikeDelta(productId, delta, event.occurredAt());
    }

    private void handleProductViewed(KafkaEventEnvelope event) {
        Long productId = toLong(event.payload().get("productId"));
        productMetricsAggregationService.applyView(productId, event.occurredAt());
        realtimeRankingAggregationService.applyView(productId, event.occurredAt());
    }

    private void handleProductDwelled(KafkaEventEnvelope event) {
        Long productId = toLong(event.payload().get("productId"));
        Long userId = toLong(event.payload().get("userId"));
        int dwellTimeSeconds = toLong(event.payload().get("dwellTimeSeconds")).intValue();
        realtimeRankingAggregationService.applyDwell(productId, userId, dwellTimeSeconds, event.occurredAt());
    }

    private void handleOrderPlaced(KafkaEventEnvelope event) {
        Object itemsRaw = event.payload().get("items");
        if (!(itemsRaw instanceof List<?> items)) {
            return;
        }

        for (Object itemRaw : items) {
            if (!(itemRaw instanceof Map<?, ?> item)) {
                continue;
            }
            Long productId = toLong(item.get("productId"));
            long quantity = toLong(item.get("quantity"));
            long unitPrice = toLong(item.get("unitPrice"));
            productMetricsAggregationService.applySales(productId, quantity, event.occurredAt());
            realtimeRankingAggregationService.applyOrder(productId, unitPrice, quantity, event.occurredAt());
        }
    }

    private KafkaEventEnvelope parse(Object rawValue) {
        try {
            if (rawValue instanceof byte[] bytes) {
                return objectMapper.readValue(bytes, KafkaEventEnvelope.class);
            }
            if (rawValue instanceof String text) {
                return objectMapper.readValue(text, KafkaEventEnvelope.class);
            }
            return objectMapper.convertValue(rawValue, KafkaEventEnvelope.class);
        } catch (Exception e) {
            String sample = rawValue instanceof byte[] bytes
                ? new String(bytes, StandardCharsets.UTF_8)
                : String.valueOf(rawValue);
            throw new IllegalStateException("Failed to parse kafka event: " + sample, e);
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
