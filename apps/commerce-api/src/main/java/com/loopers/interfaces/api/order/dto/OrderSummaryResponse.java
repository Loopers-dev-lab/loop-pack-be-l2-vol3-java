package com.loopers.interfaces.api.order.dto;

import com.loopers.application.order.OrderQueryUseCase;

import java.time.LocalDateTime;

public record OrderSummaryResponse(
        Long id,
        String status,
        int paymentAmount,
        LocalDateTime createdAt
) {
    public static OrderSummaryResponse from(OrderQueryUseCase.OrderSummary summary) {
        return new OrderSummaryResponse(
                summary.id(),
                summary.status(),
                summary.paymentAmount(),
                summary.createdAt()
        );
    }
}
