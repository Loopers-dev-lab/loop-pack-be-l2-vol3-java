package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponEvent;
import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.order.OrderEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class OutboxEventHandler {

    private static final String CATALOG_TOPIC = "catalog-events";
    private static final String ORDER_TOPIC = "order-events";
    private static final String COUPON_ISSUE_TOPIC = "coupon-issue-requests";

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(LikeEvent.Created event) {
        String eventId = UUID.randomUUID().toString();
        String payload = toPayload(Map.of(
                "eventId", eventId,
                "eventType", "LIKE_CREATED",
                "userId", event.userId(),
                "productId", event.productId(),
                "delta", 1,
                "occurredAt", ZonedDateTime.now().toString()
        ));
        outboxEventJpaRepository.save(OutboxEvent.of(eventId, CATALOG_TOPIC, event.productId().toString(), payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(LikeEvent.Deleted event) {
        String eventId = UUID.randomUUID().toString();
        String payload = toPayload(Map.of(
                "eventId", eventId,
                "eventType", "LIKE_DELETED",
                "userId", event.userId(),
                "productId", event.productId(),
                "delta", -1,
                "occurredAt", ZonedDateTime.now().toString()
        ));
        outboxEventJpaRepository.save(OutboxEvent.of(eventId, CATALOG_TOPIC, event.productId().toString(), payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderEvent.Created event) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("eventType", "ORDER_CREATED");
        data.put("userId", event.userId());
        data.put("orderId", event.orderId());
        data.put("totalAmount", event.totalAmount());
        data.put("items", event.items());
        data.put("occurredAt", ZonedDateTime.now().toString());

        String payload = toPayload(data);
        outboxEventJpaRepository.save(OutboxEvent.of(eventId, ORDER_TOPIC, event.orderId(), payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(CouponEvent.IssueRequested event) {
        String eventId = UUID.randomUUID().toString();
        String payload = toPayload(Map.of(
                "eventId", eventId,
                "requestId", event.requestId(),
                "couponId", event.couponId(),
                "userId", event.userId()
        ));
        outboxEventJpaRepository.save(OutboxEvent.of(eventId, COUPON_ISSUE_TOPIC, event.couponId().toString(), payload));
    }

    private String toPayload(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "이벤트 직렬화에 실패했습니다.");
        }
    }
}
