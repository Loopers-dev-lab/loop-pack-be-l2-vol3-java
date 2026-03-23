package com.loopers.domain.payment.gateway;

public record PgResult() {

    public record Confirm(boolean success, String paymentKey, String message, Long amount) {
        public static Confirm of(boolean success, String paymentKey, String message, Long amount) {
            return new Confirm(success, paymentKey, message, amount);
        }
    }

    public record Cancel(boolean success, String message) {
        public static Cancel of(boolean success, String message) {
            return new Cancel(success, message);
        }
    }

    public record Query(boolean found, boolean done, String status, Long amount) {
        public static Query of(boolean found, boolean done, String status, Long amount) {
            return new Query(found, done, status, amount);
        }
    }
}
