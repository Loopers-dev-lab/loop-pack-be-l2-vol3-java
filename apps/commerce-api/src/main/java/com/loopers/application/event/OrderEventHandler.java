package com.loopers.application.event;

import com.loopers.domain.order.event.OrderCancelledEvent;
import com.loopers.domain.order.event.OrderCreatedEvent;
import com.loopers.domain.order.event.OrderExpiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 이벤트 핸들러.
 *
 * <p>트랜잭션 커밋 후(AFTER_COMMIT) 비동기로 실행되며,
 * 주문 생성/취소/만료 이벤트를 처리한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    /**
     * 주문 생성 이벤트를 처리한다 (로깅).
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[OrderCreated] orderId={}, userId={}, orderType={}, totalAmount={}",
                event.orderId(), event.userId(), event.orderType(), event.totalAmount());
    }

    /**
     * 주문 취소 이벤트를 처리한다 (로깅).
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("[OrderCancelled] orderId={}, userId={}",
                event.orderId(), event.userId());
    }

    /**
     * 주문 만료 이벤트를 처리한다 (로깅).
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderExpired(OrderExpiredEvent event) {
        log.info("[OrderExpired] orderId={}, userId={}",
                event.orderId(), event.userId());
    }
}
