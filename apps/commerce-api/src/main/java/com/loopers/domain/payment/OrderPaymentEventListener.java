package com.loopers.domain.payment;

import com.loopers.domain.order.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderPaymentEventListener {

    private final PaymentService paymentService;
    private final ExternalPaymentClient externalPaymentClient;

    @Value("${payment.callback-url}")
    private String callbackUrl;

    /**
     * 주문 생성 커밋 후 비동기로 결제(PENDING) 생성 및 PG 호출.
     * - 결제 생성 실패 시 주문에 영향 없음 (이벤트 분리)
     * - PG 호출 실패 시 PENDING 상태로 유지, syncPayment로 재동기화 가능
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        paymentService.createPending(
            event.orderId(), event.memberId(), event.cardType(), event.cardNo(), event.amount()
        );

        try {
            externalPaymentClient.requestPayment(
                event.orderId(), event.cardType(), event.cardNo(), event.amount(), callbackUrl
            );
        } catch (Exception e) {
            log.warn("PG 결제 요청 실패. orderId={}, 이유={}", event.orderId(), e.getMessage());
        }
    }
}