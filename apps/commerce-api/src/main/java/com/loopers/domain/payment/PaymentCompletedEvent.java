package com.loopers.domain.payment;

public record PaymentCompletedEvent(Long orderId, Long memberId, long amount) {}