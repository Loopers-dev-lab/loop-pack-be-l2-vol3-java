package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderCancelSagaProgress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_cancel_saga_progress")
public class OrderCancelSagaProgressEntity {

    @Id
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;

    @Column(name = "coupon_done", nullable = false)
    private boolean couponDone;

    @Column(name = "point_done", nullable = false)
    private boolean pointDone;

    @Column(name = "stock_done", nullable = false)
    private boolean stockDone;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    protected OrderCancelSagaProgressEntity() {
    }

    public static OrderCancelSagaProgressEntity from(OrderCancelSagaProgress progress) {
        OrderCancelSagaProgressEntity entity = new OrderCancelSagaProgressEntity();
        entity.orderId = progress.orderId();
        entity.couponDone = progress.couponDone();
        entity.pointDone = progress.pointDone();
        entity.stockDone = progress.stockDone();
        entity.lastError = progress.lastError();
        entity.retryCount = progress.retryCount();
        return entity;
    }

    public void updateFrom(OrderCancelSagaProgress progress) {
        this.couponDone = progress.couponDone();
        this.pointDone = progress.pointDone();
        this.stockDone = progress.stockDone();
        this.lastError = progress.lastError();
        this.retryCount = progress.retryCount();
    }

    public OrderCancelSagaProgress toDomain() {
        return new OrderCancelSagaProgress(orderId, couponDone, pointDone, stockDone, lastError, retryCount);
    }
}
