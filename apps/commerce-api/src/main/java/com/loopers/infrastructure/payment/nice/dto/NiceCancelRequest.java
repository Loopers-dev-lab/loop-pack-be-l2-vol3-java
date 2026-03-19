package com.loopers.infrastructure.payment.nice.dto;

public record NiceCancelRequest(
        String reason,
        String orderId,
        Long cancelAmt
) {
}
