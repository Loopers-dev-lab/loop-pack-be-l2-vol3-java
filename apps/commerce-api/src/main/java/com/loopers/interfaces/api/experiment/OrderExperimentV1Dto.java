package com.loopers.interfaces.api.experiment;

import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderExperimentV1Dto {

    // Response

    public record UserOrderResponse(
            Long id,
            OrderStatus status,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            Long issuedCouponId,
            LocalDateTime createdAt
    ) {
        public static UserOrderResponse from(OrderInfo.OrderSummary summary) {
            return new UserOrderResponse(
                    summary.id(),
                    summary.status(),
                    summary.totalAmount(),
                    summary.discountAmount(),
                    summary.finalAmount(),
                    summary.issuedCouponId(),
                    summary.createdAt()
            );
        }
    }

    public record AdminOrderResponse(
            Long id,
            Long userId,
            OrderStatus status,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            Long issuedCouponId,
            LocalDateTime createdAt
    ) {
        public static AdminOrderResponse from(OrderInfo.OrderAdminSummary summary) {
            return new AdminOrderResponse(
                    summary.id(),
                    summary.userId(),
                    summary.status(),
                    summary.totalAmount(),
                    summary.discountAmount(),
                    summary.finalAmount(),
                    summary.issuedCouponId(),
                    summary.createdAt()
            );
        }
    }
}
