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
 * 주문 이벤트 리스너 — Outbox 저장 (BEFORE_COMMIT)
 *
 * 설계 근거:
 *   Facade는 도메인 이벤트만 발행하고, Outbox/Kafka 인프라를 모른다.
 *   이 리스너가 BEFORE_COMMIT 시점에 Outbox에 저장한다.
 *
 *   BEFORE_COMMIT = 같은 TX 안:
 *     → 비즈니스 데이터 + Outbox 이벤트가 원자적으로 커밋/롤백
 *     → Outbox 패턴의 핵심("같은 TX") 충족
 *
 *   리스너 실패 시:
 *     → TX 전체 롤백 (비즈니스 포함)
 *     → Outbox INSERT 실패 = DB 자체 문제 → 비즈니스도 실패했을 가능성 높음
 *     → 롤백이 오히려 정합성을 보호
 *
 *   Facade 결합도:
 *     → Facade는 OrderConfirmedEvent만 알면 됨
 *     → Outbox → CDC 전환 시 이 리스너만 수정, Facade 변경 없음
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
     * BEFORE_COMMIT: 비즈니스 TX가 커밋되기 직전에 실행
     * → 같은 TX 안에서 Outbox INSERT → 원자성 보장
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        outboxEventService.save(
                "ORDER",
                event.orderId(),
                "OrderConfirmedEvent",
                event,
                "order-events-v1",
                String.valueOf(event.orderId())
        );
        log.info("[OrderEventListener] Outbox 저장 (BEFORE_COMMIT) — orderId={}, topic=order-events-v1",
                event.orderId());
    }

    /**
     * 상품 판매 → Outbox에 판매량 집계 이벤트 저장
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderItemSold(OrderItemSoldEvent event) {
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
    }

    public record ProductSoldPayload(Long orderId, Long productId, int quantity) {
    }
}
