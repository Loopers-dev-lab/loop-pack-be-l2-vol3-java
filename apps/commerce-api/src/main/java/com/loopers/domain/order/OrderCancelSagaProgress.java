package com.loopers.domain.order;

import java.util.UUID;

public record OrderCancelSagaProgress(
        UUID orderId,
        boolean couponDone,
        boolean pointDone,
        boolean stockDone,
        String lastError,
        int retryCount
) {
    public static OrderCancelSagaProgress initialize(UUID orderId, boolean couponRequired, boolean pointRequired, boolean stockRequired) {
        return new OrderCancelSagaProgress(orderId, !couponRequired, !pointRequired, !stockRequired, null, 0);
    }

    public OrderCancelSagaProgress markCouponDone() {
        return new OrderCancelSagaProgress(orderId, true, pointDone, stockDone, null, retryCount);
    }

    public OrderCancelSagaProgress markPointDone() {
        return new OrderCancelSagaProgress(orderId, couponDone, true, stockDone, null, retryCount);
    }

    public OrderCancelSagaProgress markStockDone() {
        return new OrderCancelSagaProgress(orderId, couponDone, pointDone, true, null, retryCount);
    }

    public OrderCancelSagaProgress markError(String error) {
        return new OrderCancelSagaProgress(orderId, couponDone, pointDone, stockDone, error, retryCount + 1);
    }

    public boolean isCompleted() {
        return couponDone && pointDone && stockDone;
    }

    public boolean canRetry() {
        return !isCompleted() && lastError != null && retryCount < 3;
    }
}
