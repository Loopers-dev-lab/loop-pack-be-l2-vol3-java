package com.loopers.infrastructure.outbox;

import com.loopers.domain.like.LikeCancelledEvent;
import com.loopers.domain.like.LikeCreatedEvent;
import com.loopers.domain.order.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * 도메인 이벤트를 Outbox 테이블에 기록하는 BEFORE_COMMIT 리스너.
 *
 * 29CM 방식: 도메인 이벤트 발행 후, 커밋 직전에 Outbox에 기록.
 * 같은 트랜잭션에 참여하므로 도메인 데이터와 Outbox 기록의 원자성이 보장된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxEventRecordListener {

    private final OutboxRecorder outboxRecorder;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onLikeCreated(LikeCreatedEvent event) {
        outboxRecorder.record("PRODUCT", event.productId(), "LIKE_CREATED",
                Map.of("productId", event.productId(), "userId", event.userId()),
                OutboxTopics.CATALOG_EVENTS, String.valueOf(event.productId()));
        log.debug("[Outbox 기록] LIKE_CREATED: productId={}", event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onLikeCancelled(LikeCancelledEvent event) {
        outboxRecorder.record("PRODUCT", event.productId(), "LIKE_CANCELLED",
                Map.of("productId", event.productId(), "userId", event.userId()),
                OutboxTopics.CATALOG_EVENTS, String.valueOf(event.productId()));
        log.debug("[Outbox 기록] LIKE_CANCELLED: productId={}", event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        outboxRecorder.record("ORDER", event.orderId(), "ORDER_CREATED",
                Map.of("orderId", event.orderId(), "userId", event.userId(),
                        "totalAmount", event.totalAmount(), "itemCount", event.items().size()),
                OutboxTopics.ORDER_EVENTS, String.valueOf(event.orderId()));
        log.debug("[Outbox 기록] ORDER_CREATED: orderId={}", event.orderId());
    }
}
