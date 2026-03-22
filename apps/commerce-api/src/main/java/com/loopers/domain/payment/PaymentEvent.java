package com.loopers.domain.payment;

public class PaymentEvent {

    public record PaymentSucceed(Long paymentId, Long orderId) {

        public static PaymentSucceed from(Payment payment) {
            return new PaymentSucceed(payment.getId(), payment.getOrderId());
        }
    }

    public record PaymentFailed(Long paymentId, Long orderId) {

        public static PaymentFailed from(Payment payment) {
            return new PaymentFailed(payment.getId(), payment.getOrderId());
        }
    }
}
