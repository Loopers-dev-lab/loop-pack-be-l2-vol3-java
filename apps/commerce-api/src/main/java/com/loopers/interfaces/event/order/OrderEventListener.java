package com.loopers.interfaces.event.order;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주문 도메인의 이벤트 리스너.
 *
 * <p>결제 이벤트를 수신하여 주문 상태를 변경한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderService orderService;

    /**
     * 결제 성공 이벤트를 처리한다.
     *
     * <p>해당 주문을 결제 완료 상태로 변경한다.</p>
     *
     * @param event 결제 성공 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(PaymentEvent.PaymentSucceed event) {
        log.info("[EVENT:PaymentSucceed:order] paymentId={}, orderId={}", event.paymentId(), event.orderId());
        try {
            orderService.pay(event.orderId());
        } catch (Exception e) {
            log.error("결제 성공 이벤트 처리 실패: 주문 결제 완료 [orderId={}]", event.orderId(), e);
        }
    }

    /**
     * 결제 실패 이벤트를 처리한다.
     *
     * <p>해당 주문을 실패 상태로 변경한다.</p>
     *
     * @param event 결제 실패 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(PaymentEvent.PaymentFailed event) {
        log.info("[EVENT:PaymentFailed:order] paymentId={}, orderId={}", event.paymentId(), event.orderId());
        try {
            orderService.fail(event.orderId());
        } catch (Exception e) {
            log.error("결제 실패 이벤트 처리 실패: 주문 실패 처리 [orderId={}]", event.orderId(), e);
        }
    }
}
