package com.loopers.domain.payment.gateway;

public record PgCommand() {

    public record Confirm(String paymentKey, String orderId, Long amount) {
        public static Confirm of(String paymentKey, String orderId, Long amount) {
            return new Confirm(paymentKey, orderId, amount);
        }
    }

    public record Cancel(String orderId, String cancelReason, Long cancelAmount) {
        public static Cancel of(String orderId, String cancelReason, Long cancelAmount) {
            return new Cancel(orderId, cancelReason, cancelAmount);
        }
    }
}
