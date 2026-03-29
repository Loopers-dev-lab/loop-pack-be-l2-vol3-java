package com.loopers.domain.payment;

import java.util.UUID;

public class PaymentEvent {

    public record PaymentSucceed(UUID eventId, Long paymentId, Long orderId) {

        public static PaymentSucceed from(Payment payment) {
            return new PaymentSucceed(UUID.randomUUID(), payment.getId(), payment.getOrderId());
        }
    }

    public record PaymentFailed(UUID eventId, Long paymentId, Long orderId) {

        public static PaymentFailed from(Payment payment) {
            return new PaymentFailed(UUID.randomUUID(), payment.getId(), payment.getOrderId());
        }
    }
}
