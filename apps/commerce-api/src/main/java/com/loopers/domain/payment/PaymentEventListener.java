package com.loopers.domain.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class PaymentEventListener {

    /**
     * 결제 완료 커밋 후 비동기로 알림/로그 처리.
     * AFTER_COMMIT: 결제 트랜잭션이 커밋된 경우에만 실행 (롤백 시 실행 안 됨).
     * REQUIRES_NEW: 독립 트랜잭션으로 실행 — 리스너 실패가 결제 트랜잭션에 영향 없음.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[알림] 결제가 완료되었습니다. orderId={}, memberId={}, amount={}",
            event.orderId(), event.memberId(), event.amount());
    }
}