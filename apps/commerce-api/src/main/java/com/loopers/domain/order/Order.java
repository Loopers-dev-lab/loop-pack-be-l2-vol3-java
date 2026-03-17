package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.List;

@Getter
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    @Column(name = "discount_amount", nullable = false)
    private Long discountAmount;

    @Column(name = "final_amount", nullable = false)
    private Long finalAmount;

    @Column(name = "issued_coupon_id")
    private Long issuedCouponId;

    protected Order() {}

    private Order(Long userId, List<OrderItemSnapshot> orderItemSnapshots, Long discountAmount, Long issuedCouponId) {
        this.userId = userId;
        this.status = Status.ORDERED;
        this.originalAmount = calculateOriginalAmount(orderItemSnapshots);
        this.discountAmount = discountAmount;
        this.finalAmount = calculateFinalAmount();
        this.issuedCouponId = issuedCouponId;
    }

    private Long calculateOriginalAmount(List<OrderItemSnapshot> orderItemSnapshots) {
        return orderItemSnapshots.stream()
                                 .mapToLong(OrderItemSnapshot::lineAmount)
                                 .sum();
    }

    private Long calculateFinalAmount() {
        return originalAmount - discountAmount;
    }

    public static Order create(Long userId, List<OrderItemSnapshot> orderItemSnapshots) {
        return create(userId, orderItemSnapshots, 0L, null);
    }

    public static Order create(Long userId, List<OrderItemSnapshot> orderItemSnapshots, Long discountAmount, Long issuedCouponId) {
        if (orderItemSnapshots == null || orderItemSnapshots.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }

        return new Order(userId, orderItemSnapshots, discountAmount, issuedCouponId);
    }

    public void markPaid() {
        if (status != Status.ORDERED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 완료 처리는 ORDERED 상태에서만 가능합니다.");
        }

        this.status = Status.PAID;
    }

    public void markFailed() {
        if (status != Status.ORDERED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 실패 처리는 ORDERED 상태에서만 가능합니다.");
        }
        this.status = Status.FAILED;
    }

    public enum Status {
        ORDERED,
        PAID,
        FAILED,
    }
}
