package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import com.loopers.domain.like.event.LikeToggledEvent;
import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventEnvelope;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.domain.product.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * BEFORE_COMMIT: 도메인 TX와 같은 TX에서 outbox 저장.
 * 도메인 이벤트가 커밋되면 outbox 레코드도 함께 커밋된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleLikeToggled(LikeToggledEvent event) {
        String eventType = event.liked() ? "LIKED" : "UNLIKED";
        saveOutboxEvent("Product", event.productId(), eventType,
            "catalog-events", String.valueOf(event.productId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleProductViewed(ProductViewedEvent event) {
        saveOutboxEvent("Product", event.productId(), "PRODUCT_VIEWED",
            "catalog-events", String.valueOf(event.productId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        saveOutboxEvent("Order", event.orderId(), "ORDER_PLACED",
            "order-events", String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        saveOutboxEvent("Payment", event.paymentId(), "PAYMENT_COMPLETED",
            "order-events", String.valueOf(event.orderId()), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleCouponIssueRequested(CouponIssueRequestedEvent event) {
        saveOutboxEvent("Coupon", event.couponId(), "COUPON_ISSUE_REQUESTED",
            "coupon-issue-requests", String.valueOf(event.couponId()), event);
    }

    private void saveOutboxEvent(String aggregateType, Long aggregateId, String eventType,
                                  String topic, String partitionKey, Object eventData) {
        String eventId = UUID.randomUUID().toString();
        OutboxEventEnvelope envelope = new OutboxEventEnvelope(eventId, eventType, ZonedDateTime.now(), eventData);

        try {
            String payload = objectMapper.writeValueAsString(envelope);
            OutboxEvent outboxEvent = new OutboxEvent(
                aggregateType, aggregateId, eventType, topic, partitionKey, payload
            );
            outboxRepository.save(outboxEvent);
            log.debug("Outbox 이벤트 저장: eventType={}, aggregateId={}", eventType, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("Outbox 이벤트 직렬화 실패: eventType={}, aggregateId={}", eventType, aggregateId, e);
            throw new RuntimeException("Outbox 이벤트 직렬화 실패", e);
        }
    }
}
