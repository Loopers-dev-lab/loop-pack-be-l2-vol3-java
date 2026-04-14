package com.loopers.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.OutboxEventModel;
import com.loopers.infrastructure.event.OutboxEventJpaRepository;
import com.loopers.kafka.message.KafkaEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final ObjectMapper objectMapper;

    @Value("${commerce.kafka.topics.catalog-events}")
    private String catalogEventsTopic;

    @Value("${commerce.kafka.topics.order-events}")
    private String orderEventsTopic;

    @Value("${commerce.kafka.topics.coupon-issue-requests}")
    private String couponIssueRequestsTopic;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderPlaced(AppEvents.OrderPlacedApplicationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", event.orderId());
        payload.put("userId", event.userId());
        payload.put("items", event.items());
        payload.put("totalAmount", event.totalAmount());

        write(event.eventId(), orderEventsTopic, String.valueOf(event.orderId()), new KafkaEventEnvelope(
            event.eventId(),
            "ORDER_PLACED",
            "ORDER",
            String.valueOf(event.orderId()),
            String.valueOf(event.orderId()),
            1,
            event.occurredAt(),
            payload
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleLikeChanged(AppEvents.ProductLikeChangedApplicationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", event.productId());
        payload.put("userId", event.userId());
        payload.put("delta", event.delta());

        write(event.eventId(), catalogEventsTopic, String.valueOf(event.productId()), new KafkaEventEnvelope(
            event.eventId(),
            "PRODUCT_LIKE_CHANGED",
            "PRODUCT",
            String.valueOf(event.productId()),
            String.valueOf(event.productId()),
            1,
            event.occurredAt(),
            payload
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleCouponIssueRequested(AppEvents.CouponIssueRequestedApplicationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestId", event.requestId());
        payload.put("couponId", event.couponId());
        payload.put("userId", event.userId());

        write(event.eventId(), couponIssueRequestsTopic, String.valueOf(event.couponId()), new KafkaEventEnvelope(
            event.eventId(),
            "COUPON_ISSUE_REQUESTED",
            "COUPON",
            String.valueOf(event.couponId()),
            String.valueOf(event.couponId()),
            1,
            event.occurredAt(),
            payload
        ));
    }

    @EventListener
    public void handleDwelled(AppEvents.ProductDwelledApplicationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", event.productId());
        payload.put("userId", event.userId());
        payload.put("dwellTimeSeconds", event.dwellTimeSeconds());

        write(event.eventId(), catalogEventsTopic, String.valueOf(event.productId()), new KafkaEventEnvelope(
            event.eventId(),
            "PRODUCT_DWELLED",
            "PRODUCT",
            String.valueOf(event.productId()),
            String.valueOf(event.productId()),
            1,
            event.occurredAt(),
            payload
        ));
    }

    @EventListener
    public void handleViewed(AppEvents.ProductViewedApplicationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", event.productId());
        payload.put("userId", event.userId());

        write(event.eventId(), catalogEventsTopic, String.valueOf(event.productId()), new KafkaEventEnvelope(
            event.eventId(),
            "PRODUCT_VIEWED",
            "PRODUCT",
            String.valueOf(event.productId()),
            String.valueOf(event.productId()),
            1,
            event.occurredAt(),
            payload
        ));
    }

    private void write(String eventId, String topic, String key, KafkaEventEnvelope envelope) {
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            outboxEventJpaRepository.save(new OutboxEventModel(eventId, topic, key, payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event. eventId={}", eventId, e);
            throw new IllegalStateException("Failed to serialize event payload", e);
        }
    }
}
