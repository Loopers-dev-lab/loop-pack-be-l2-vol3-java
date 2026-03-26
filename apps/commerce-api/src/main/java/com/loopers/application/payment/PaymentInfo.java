package com.loopers.application.payment;

import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import java.time.ZonedDateTime;

public record PaymentInfo(
    Long id,
    Long orderId,
    Long amount,
    String cardType,
    PaymentStatus status,
    OrderStatus orderStatus,
    String pgPaymentKey,
    String lastError,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static PaymentInfo from(PaymentModel model, OrderStatus orderStatus) {
        return new PaymentInfo(
            model.getId(),
            model.getOrderId(),
            model.getAmount(),
            model.getCardType().name(),
            model.getStatus(),
            orderStatus,
            model.getPgPaymentKey(),
            model.getLastError(),
            model.getCreatedAt(),
            model.getUpdatedAt()
        );
    }
}
