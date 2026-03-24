package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.like.event.LikeEvent;
import com.loopers.application.order.event.OrderCancelledEvent;
import com.loopers.application.order.event.OrderCreatedEvent;
import com.loopers.application.payment.event.PaymentCompletedEvent;
import com.loopers.application.payment.event.PaymentFailedEvent;
import com.loopers.application.coupon.event.CouponIssueRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private final OutboxJpaRepository outboxJpaRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleLikeEvent(LikeEvent event) {
        String eventType = event.action() == LikeEvent.LikeAction.LIKED ? "LIKED" : "UNLIKED";
        saveOutbox("PRODUCT", event.productId(), eventType,
            "catalog-events", String.valueOf(event.productId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        saveOutbox("ORDER", event.orderId(), "ORDER_CREATED",
            "order-events", String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        saveOutbox("ORDER", event.orderId(), "ORDER_CANCELLED",
            "order-events", String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        saveOutbox("PAYMENT", event.orderId(), "PAYMENT_COMPLETED",
            "order-events", String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        saveOutbox("PAYMENT", event.orderId(), "PAYMENT_FAILED",
            "order-events", String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleCouponIssueRequested(CouponIssueRequestedEvent event) {
        saveOutbox("COUPON", event.couponId(), "COUPON_ISSUE_REQUESTED",
            "coupon-issue-requests", String.valueOf(event.couponId()), event);
    }

    private void saveOutbox(String aggregateType, Long aggregateId, String eventType,
                            String topic, String partitionKey, Object event) {
        try {
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> envelope = Map.of(
                "eventId", eventId,
                "eventType", eventType,
                "data", event
            );
            String payload = objectMapper.writeValueAsString(envelope);
            OutboxEvent outboxEvent = new OutboxEvent(
                aggregateType, aggregateId, eventType, eventId, topic, partitionKey, payload);
            outboxJpaRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("[Outbox 직렬화 실패] eventType={}, error={}", eventType, e.getMessage());
        }
    }
}
