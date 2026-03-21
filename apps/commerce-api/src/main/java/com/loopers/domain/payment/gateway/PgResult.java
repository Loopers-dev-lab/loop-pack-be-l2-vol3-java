package com.loopers.domain.payment.gateway;

public record PgResult() {

    public record Confirm(boolean success, String paymentKey, String message) {
        public static Confirm of(boolean success, String paymentKey, String message) {
            return new Confirm(success, paymentKey, message);
        }
    }

    public record Cancel(boolean success, String message) {
        public static Cancel of(boolean success, String message) {
            return new Cancel(success, message);
        }
    }

    public record Query(boolean found, boolean done, String status) {
        public static Query of(boolean found, boolean done, String status) {
            return new Query(found, done, status);
        }
    }
}
