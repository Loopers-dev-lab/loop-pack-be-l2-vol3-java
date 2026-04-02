package com.loopers.application.event.listener;

import com.loopers.application.event.OrderCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 알림 이벤트 리스너.
 * 주문 완료 시 알림 발송 (현재는 로그로 대체).
 */
@Slf4j
@Component
public class OrderNotificationListener {

    /**
     * 주문 완료 시 알림 발송.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.info("주문 완료 알림: orderId={}, userId={}, totalAmount={}",
            event.orderId(), event.userId(), event.totalAmount());
    }
}
