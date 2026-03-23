package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

public record PaymentInfo(
    Long paymentId,
    Long orderId,
    PaymentStatus paymentStatus,
    OrderStatus orderStatus,
    String pgTransactionId,
    String failReason,
    Integer totalPrice
) {
    public static PaymentInfo from(Payment payment, Order order) {
        return new PaymentInfo(
            payment.getId(),
            order.getId(),
            payment.getStatus(),
            order.getStatus(),
            payment.getPgTransactionId(),
            payment.getFailReason(),
            order.getTotalPrice().value()
        );
    }
}
