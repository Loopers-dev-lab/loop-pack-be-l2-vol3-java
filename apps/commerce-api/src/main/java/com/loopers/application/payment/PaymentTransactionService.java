package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderHistoryService;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final OrderService orderService;
    private final OrderHistoryService orderHistoryService;
    private final PaymentService paymentService;

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

        orderHistoryService.recordHistory(
                payment.getOrderId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAID, historyMessage
        );
    }

    @Transactional
    public void failPayment(Payment payment, Long orderId, String reason, String historyMessage) {
        payment.markFailed(reason);

        Order order = orderService.getById(orderId);
        order.failPayment();

        orderHistoryService.recordHistory(
                orderId, OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_FAILED, historyMessage
        );
    }
}
