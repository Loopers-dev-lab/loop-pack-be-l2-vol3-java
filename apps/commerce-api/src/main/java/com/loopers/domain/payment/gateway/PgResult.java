package com.loopers.domain.payment.gateway;

public record PgResult() {

    public record Confirm(boolean success, String paymentKey, String message) {}

    public record Cancel(boolean success, String message) {}

    public record Query(boolean found, boolean done, String status) {}
}
