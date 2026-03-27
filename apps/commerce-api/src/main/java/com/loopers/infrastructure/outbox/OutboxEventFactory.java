package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.event.CouponIssueRequestedEvent;
import com.loopers.application.event.PaymentCanceledEvent;
import com.loopers.application.event.PaymentCompletedEvent;
import com.loopers.application.event.PaymentFailedEvent;
import com.loopers.application.event.ProductLikedEvent;
import com.loopers.application.event.ProductUnlikedEvent;
import com.loopers.support.outbox.DomainEvent;
import com.loopers.support.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEvent create(DomainEvent event) {
        EventMetadata metadata = resolveMetadata(event);
        if (metadata == null) {
            return null;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("이벤트 직렬화 실패: {}", event.getClass().getSimpleName(), e);
            return null;
        }

        return OutboxEvent.create(
                UUID.randomUUID().toString(),
                metadata.eventType(),
                metadata.aggregateType(),
                metadata.aggregateId(),
                payload,
                metadata.topic()
        );
    }

    private EventMetadata resolveMetadata(DomainEvent event) {
        return switch (event) {
            case ProductLikedEvent e -> new EventMetadata(
                    "product.liked", "Product", String.valueOf(e.productId()), "catalog-events");
            case ProductUnlikedEvent e -> new EventMetadata(
                    "product.unliked", "Product", String.valueOf(e.productId()), "catalog-events");
            case PaymentCompletedEvent e -> new EventMetadata(
                    "payment.completed", "Order", String.valueOf(e.orderId()), "order-events");
            case PaymentFailedEvent e -> new EventMetadata(
                    "payment.failed", "Order", String.valueOf(e.orderId()), "order-events");
            case PaymentCanceledEvent e -> new EventMetadata(
                    "payment.canceled", "Order", String.valueOf(e.orderId()), "order-events");
            case CouponIssueRequestedEvent e -> new EventMetadata(
                    "coupon.issue.requested", "Coupon", String.valueOf(e.couponId()), "coupon-issue-requests");
            default -> {
                log.warn("미지원 이벤트 타입: {}", event.getClass().getSimpleName());
                yield null;
            }
        };
    }

    private record EventMetadata(String eventType, String aggregateType, String aggregateId, String topic) {
    }
}
