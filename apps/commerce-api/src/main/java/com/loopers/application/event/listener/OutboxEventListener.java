package com.loopers.application.event.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.event.CouponIssueRequestCreatedEvent;
import com.loopers.application.event.LikeCanceledEvent;
import com.loopers.application.event.LikeCreatedEvent;
import com.loopers.application.event.OrderCompletedEvent;
import com.loopers.application.event.ProductViewedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.event.AggregateType;
import com.loopers.event.EventType;
import com.loopers.event.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Transactional Outbox Pattern을 위한 이벤트 리스너.
 * 트랜잭션 커밋 전에 Outbox 테이블에 이벤트를 저장.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleLikeCreatedEvent(LikeCreatedEvent event) {
        saveOutboxEvent(
            EventType.LIKE_CREATED,
            AggregateType.PRODUCT,
            String.valueOf(event.productId()),
            KafkaTopics.CATALOG_EVENTS,
            event
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleLikeCanceledEvent(LikeCanceledEvent event) {
        saveOutboxEvent(
            EventType.LIKE_CANCELED,
            AggregateType.PRODUCT,
            String.valueOf(event.productId()),
            KafkaTopics.CATALOG_EVENTS,
            event
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCompletedEvent(OrderCompletedEvent event) {
        saveOutboxEvent(
            EventType.ORDER_COMPLETED,
            AggregateType.ORDER,
            String.valueOf(event.orderId()),
            KafkaTopics.ORDER_EVENTS,
            event
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleProductViewedEvent(ProductViewedEvent event) {
        saveOutboxEvent(
            EventType.PRODUCT_VIEWED,
            AggregateType.PRODUCT,
            String.valueOf(event.productId()),
            KafkaTopics.CATALOG_EVENTS,
            event
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleCouponIssueRequestCreatedEvent(CouponIssueRequestCreatedEvent event) {
        saveOutboxEvent(
            EventType.COUPON_ISSUE_REQUESTED,
            AggregateType.COUPON,
            String.valueOf(event.couponId()),
            KafkaTopics.COUPON_ISSUE_REQUESTS,
            event
        );
    }

    private void saveOutboxEvent(
        EventType eventType,
        AggregateType aggregateType,
        String aggregateId,
        String topic,
        Object event
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.create(
                eventType.name(),
                aggregateType.name(),
                aggregateId,
                topic,
                payload
            );
            outboxEventRepository.save(outboxEvent);
            log.debug("Outbox event saved: type={}, aggregateId={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", event, e);
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }
}
