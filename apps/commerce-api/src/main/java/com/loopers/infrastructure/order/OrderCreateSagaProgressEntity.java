package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderCreateSagaProgress;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "order_create_saga_progress")
public class OrderCreateSagaProgressEntity {
    @Id
    @Column(name = "order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID orderId;
    @Column(name = "member_id", nullable = false)
    private String memberId;
    @Column(name = "coupon_id")
    private UUID couponId;
    @Column(name = "order_amount", nullable = false)
    private int orderAmount;
    @Column(name = "requested_point_amount", nullable = false)
    private int requestedPointAmount;
    @Column(name = "payment_amount", nullable = false)
    private int paymentAmount;
    @Column(name = "card_type", nullable = false)
    private String cardType;
    @Column(name = "card_no", nullable = false)
    private String cardNo;
    @Column(name = "coupon_done", nullable = false)
    private boolean couponDone;
    @Column(name = "point_done", nullable = false)
    private boolean pointDone;
    @Column(name = "payment_requested", nullable = false)
    private boolean paymentRequested;
    @Column(name = "completed", nullable = false)
    private boolean completed;
    @Column(name = "compensated", nullable = false)
    private boolean compensated;
    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected OrderCreateSagaProgressEntity() {}

    public static OrderCreateSagaProgressEntity from(OrderCreateSagaProgress progress) {
        OrderCreateSagaProgressEntity e = new OrderCreateSagaProgressEntity();
        e.orderId = progress.orderId();
        e.memberId = progress.memberId();
        e.couponId = progress.couponId();
        e.orderAmount = progress.orderAmount();
        e.requestedPointAmount = progress.requestedPointAmount();
        e.paymentAmount = progress.paymentAmount();
        e.cardType = progress.cardType();
        e.cardNo = progress.cardNo();
        e.couponDone = progress.couponDone();
        e.pointDone = progress.pointDone();
        e.paymentRequested = progress.paymentRequested();
        e.completed = progress.completed();
        e.compensated = progress.compensated();
        e.lastError = progress.lastError();
        return e;
    }

    public void updateFrom(OrderCreateSagaProgress progress) {
        this.couponDone = progress.couponDone();
        this.memberId = progress.memberId();
        this.couponId = progress.couponId();
        this.orderAmount = progress.orderAmount();
        this.requestedPointAmount = progress.requestedPointAmount();
        this.paymentAmount = progress.paymentAmount();
        this.cardType = progress.cardType();
        this.cardNo = progress.cardNo();
        this.pointDone = progress.pointDone();
        this.paymentRequested = progress.paymentRequested();
        this.completed = progress.completed();
        this.compensated = progress.compensated();
        this.lastError = progress.lastError();
    }

    public OrderCreateSagaProgress toDomain() {
        return new OrderCreateSagaProgress(orderId, memberId, couponId, orderAmount, requestedPointAmount, paymentAmount, cardType, cardNo, couponDone, pointDone, paymentRequested, completed, compensated, lastError);
    }
}
