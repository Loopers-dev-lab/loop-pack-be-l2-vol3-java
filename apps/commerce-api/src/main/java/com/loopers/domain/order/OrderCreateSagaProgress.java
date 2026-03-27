package com.loopers.domain.order;

import java.util.UUID;

public record OrderCreateSagaProgress(
        UUID orderId,
        String memberId,
        UUID couponId,
        int orderAmount,
        int requestedPointAmount,
        int paymentAmount,
        String cardType,
        String cardNo,
        boolean couponDone,
        boolean pointDone,
        boolean paymentRequested,
        boolean completed,
        boolean compensated,
        String lastError
) {
    public static OrderCreateSagaProgress initialize(
            UUID orderId,
            String memberId,
            UUID couponId,
            int orderAmount,
            int requestedPointAmount,
            int paymentAmount,
            String cardType,
            String cardNo,
            boolean couponDone,
            boolean pointDone
    ) {
        return new OrderCreateSagaProgress(
                orderId,
                memberId,
                couponId,
                orderAmount,
                requestedPointAmount,
                paymentAmount,
                cardType,
                cardNo,
                couponDone,
                pointDone,
                false,
                false,
                false,
                null
        );
    }

    public OrderCreateSagaProgress markCouponDone() {
        return new OrderCreateSagaProgress(orderId, memberId, couponId, orderAmount, requestedPointAmount, paymentAmount, cardType, cardNo, true, pointDone, paymentRequested, completed, compensated, null);
    }

    public OrderCreateSagaProgress markPointDone() {
        return new OrderCreateSagaProgress(orderId, memberId, couponId, orderAmount, requestedPointAmount, paymentAmount, cardType, cardNo, couponDone, true, paymentRequested, completed, compensated, null);
    }

    public OrderCreateSagaProgress markPaymentRequested() {
        return new OrderCreateSagaProgress(orderId, memberId, couponId, orderAmount, requestedPointAmount, paymentAmount, cardType, cardNo, couponDone, pointDone, true, completed, compensated, null);
    }

    public OrderCreateSagaProgress markCompleted() {
        return new OrderCreateSagaProgress(orderId, memberId, couponId, orderAmount, requestedPointAmount, paymentAmount, cardType, cardNo, couponDone, pointDone, paymentRequested, true, compensated, null);
    }

    public OrderCreateSagaProgress markCompensated() {
        return new OrderCreateSagaProgress(orderId, memberId, couponId, orderAmount, requestedPointAmount, paymentAmount, cardType, cardNo, couponDone, pointDone, paymentRequested, completed, true, lastError);
    }

    public OrderCreateSagaProgress markError(String error) {
        return new OrderCreateSagaProgress(orderId, memberId, couponId, orderAmount, requestedPointAmount, paymentAmount, cardType, cardNo, couponDone, pointDone, paymentRequested, completed, compensated, error);
    }
}
