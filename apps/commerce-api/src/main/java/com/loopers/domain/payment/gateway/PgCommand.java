package com.loopers.domain.payment.gateway;

public record PgCommand() {

    public record Confirm(String paymentKey, String orderId, Long amount) {}

    public record Cancel(String orderId, String cancelReason, Long cancelAmount) {}
}
