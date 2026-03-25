package com.loopers.application.order;

import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.payment.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문/결제 이벤트 리스너.
 *
 * 주문 생성, 결제 완료 등 핵심 비즈니스 이벤트를 수신하여 부가 로직(로깅, 알림 등)을 처리한다.
 * 새로운 부가 로직이 필요하면 이 리스너에 메서드를 추가하거나 새 리스너를 만들면 된다. (Open-Closed)
 *
 * @Async + AFTER_COMMIT 조합:
 * - 핵심 트랜잭션 커밋이 확정된 후 실행 (안전)
 * - 별도 스레드에서 실행하므로 응답 지연 없음
 * - 실패해도 핵심 로직에 영향 없음
 */
@Slf4j
@Component
public class OrderEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("[주문 생성] orderId={}, userId={}, totalAmount={}, itemCount={}",
                event.orderId(), event.userId(), event.totalAmount(), event.items().size());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[결제 완료] paymentId={}, orderId={}, userId={}, amount={}, transactionKey={}",
                event.paymentId(), event.orderId(), event.userId(), event.amount(), event.transactionKey());
    }
}
