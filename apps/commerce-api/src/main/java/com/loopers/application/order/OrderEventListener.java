package com.loopers.application.order;

import com.loopers.domain.common.event.OrderConfirmedEvent;
import com.loopers.domain.common.event.OrderItemSoldEvent;
import com.loopers.domain.common.event.UserActivityEvent;
import com.loopers.domain.point.PointService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트 리스너
 *
 * AFTER_COMMIT + @Async 조합:
 *   메인 TX(TX2)가 커밋된 후에, 별도 스레드에서 실행.
 *   → 메인 응답 속도에 영향 없음.
 *   → 실패해도 메인 비즈니스 롤백 없음.
 *
 * 포인트 적립이 여기서 실패하면?
 *   → 현재: 로그만 남김 (CS 보정)
 *   → Step 2(Kafka 전환) 후: Outbox + DLQ로 자동 재처리
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final PointService pointService;

    public OrderEventListener(PointService pointService) {
        this.pointService = pointService;
    }

    /**
     * 주문 확정 → 포인트 적립
     *
     * AFTER_COMMIT: TX2 커밋 확인 후 실행 → 주문 확정이 DB에 반영된 후
     * @Async: 별도 스레드 → API 응답 지연 없음
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderConfirmed(OrderConfirmedEvent event) {
        try {
            log.info("[OrderEventListener] 포인트 적립 시작 — orderId={}, userId={}, amount={}",
                    event.orderId(), event.userId(), event.totalAmount());

            pointService.earn(event.userId(), event.totalAmount());

            log.info("[OrderEventListener] 포인트 적립 완료 — orderId={}, userId={}",
                    event.orderId(), event.userId());
        } catch (Exception e) {
            // 포인트 적립 실패 → 메인 비즈니스에 영향 없음
            // Step 2에서 Kafka + Outbox + DLQ로 전환하면 자동 재처리
            log.error("[OrderEventListener] 포인트 적립 실패 — orderId={}, userId={}, error={}",
                    event.orderId(), event.userId(), e.getMessage(), e);
        }
    }

    /**
     * 주문 확정 → 유저 행동 로깅
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderActivity(OrderConfirmedEvent event) {
        log.info("[OrderEventListener] 유저 행동 로깅 — userId={}, orderId={}, type=PAYMENT",
                event.userId(), event.orderId());
        // Step 2에서 Kafka user-activity-events 토픽으로 전환
    }
}
