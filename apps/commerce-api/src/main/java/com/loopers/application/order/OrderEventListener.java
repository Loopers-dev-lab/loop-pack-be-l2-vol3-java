package com.loopers.application.order;

import com.loopers.domain.common.event.OrderConfirmedEvent;
import com.loopers.domain.common.event.OrderItemSoldEvent;
import com.loopers.infrastructure.outbox.OutboxEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트 리스너 — Step 2 (Kafka 전환)
 *
 * Step 1에서는 @Async + pointService.earn() 직접 호출이었으나,
 * Step 2에서는 Outbox에 저장 → Relay → Kafka → commerce-streamer Consumer가 처리.
 *
 * AFTER_COMMIT 유지 (TX 커밋 확인 후 Outbox 저장):
 *   OrderFacade.processPaymentAndConfirm()은 txTemplate.execute()를 사용하므로
 *   이벤트 발행이 TX 밖에서 일어남. 따라서 AFTER_COMMIT 리스너가 트리거되려면
 *   이벤트 발행 시점에 활성 TX가 필요 → 별도 @Transactional로 Outbox 저장.
 *
 * @Async 제거:
 *   Outbox 저장은 DB INSERT 1건이라 빠름 (수 ms).
 *   비동기로 할 필요 없고, 동기로 하면 Outbox 저장 실패를 즉시 감지 가능.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OutboxEventService outboxEventService;

    public OrderEventListener(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    /**
     * 주문 확정 → Outbox에 포인트 적립 이벤트 저장
     *
     * Relay가 order-events-v1 토픽으로 발행 → commerce-streamer Consumer가 포인트 적립
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        try {
            outboxEventService.save(
                    "ORDER",
                    event.orderId(),
                    "OrderConfirmedEvent",
                    event,
                    "order-events-v1",
                    String.valueOf(event.orderId())
            );
            log.info("[OrderEventListener] Outbox 저장 — orderId={}, topic=order-events-v1",
                    event.orderId());
        } catch (Exception e) {
            log.error("[OrderEventListener] Outbox 저장 실패 — orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        }
    }

    /**
     * 상품 판매 → Outbox에 판매량 집계 이벤트 저장
     *
     * Relay가 catalog-events-v1 토픽으로 발행 → commerce-streamer Consumer가 집계
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderItemSold(OrderItemSoldEvent event) {
        try {
            for (var entry : event.productQtyMap().entrySet()) {
                outboxEventService.save(
                        "PRODUCT",
                        entry.getKey(),
                        "OrderItemSoldEvent",
                        new ProductSoldPayload(event.orderId(), entry.getKey(), entry.getValue()),
                        "catalog-events-v1",
                        String.valueOf(entry.getKey())
                );
            }
            log.info("[OrderEventListener] Outbox 저장 (판매 집계) — orderId={}, products={}",
                    event.orderId(), event.productQtyMap().size());
        } catch (Exception e) {
            log.error("[OrderEventListener] Outbox 저장 실패 (판매 집계) — orderId={}, error={}",
                    event.orderId(), e.getMessage(), e);
        }
    }

    public record ProductSoldPayload(Long orderId, Long productId, int quantity) {
    }
}
