package com.loopers.application.payment;

import com.loopers.domain.event.PaymentCompletedEvent;
import com.loopers.domain.event.PaymentFailedEvent;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderHistoryService;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final OrderService orderService;
    private final OrderHistoryService orderHistoryService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Payment preparePayment(Long orderId, Long userId, CardType cardType, String cardNo) {
        Order order = orderService.getById(orderId);
        order.validateOwner(userId);
        order.startPayment();

        orderHistoryService.recordHistory(orderId, OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, "결제 요청");

        Payment payment = paymentService.createPayment(orderId, userId, order.getTotalAmount(), cardType, cardNo);
        payment.markPending();

        return payment;
    }

    @Transactional
    public void completePayment(Payment payment, String transactionKey, String historyMessage) {
        payment.markSuccess(transactionKey);

        Order order = orderService.getById(payment.getOrderId());
        order.completePayment();

        eventPublisher.publishEvent(new PaymentCompletedEvent(
                payment.getOrderId(), payment.getUserId(), transactionKey, historyMessage
        ));
    }

    @Transactional
    public void failPayment(Payment payment, Long orderId, String reason, String historyMessage) {
        payment.markFailed(reason);

        Order order = orderService.getById(orderId);
        order.failPayment();

        eventPublisher.publishEvent(new PaymentFailedEvent(
                orderId, payment.getUserId(), order.getUserCouponId(), reason
        ));
    }
}
