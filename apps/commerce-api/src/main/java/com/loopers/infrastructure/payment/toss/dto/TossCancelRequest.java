package com.loopers.infrastructure.payment.toss.dto;

public record TossCancelRequest(
        String cancelReason,
        Long cancelAmount
) {
}
