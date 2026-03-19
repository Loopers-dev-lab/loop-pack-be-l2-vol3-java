package com.loopers.infrastructure.payment.dto;

public record TossCancelRequest(
        String cancelReason,
        Long cancelAmount
) {
}
