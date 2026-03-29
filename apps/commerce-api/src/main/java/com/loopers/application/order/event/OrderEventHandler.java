package com.loopers.application.order.event;

import com.loopers.application.payment.PaymentTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final PaymentTxService paymentTxService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        try {
            log.info("주문 생성 이벤트 - orderId: {}, userId: {}, items: {}",
                event.orderId(), event.userId(), event.items());
        } catch (Exception e) {
            log.warn("주문 생성 이벤트 처리 실패 - orderId: {}", event.orderId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        try {
            log.info("주문 취소 이벤트 - orderId: {}, userId: {}",
                event.orderId(), event.userId());
            paymentTxService.refundPayment(event.orderId());
        } catch (Exception e) {
            log.warn("주문 취소 후속 처리 실패 - orderId: {}", event.orderId(), e);
        }
    }
}
