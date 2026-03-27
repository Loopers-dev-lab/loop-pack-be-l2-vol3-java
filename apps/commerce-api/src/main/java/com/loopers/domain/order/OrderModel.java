package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class OrderModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "discount_amount", nullable = false))
    private Money discountAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "final_amount", nullable = false))
    private Money finalAmount;

    @Column(name = "coupon_issue_id")
    private Long couponIssueId;

    protected OrderModel() {
    }

    public OrderModel(Long userId, Money totalAmount, Money discountAmount, Long couponIssueId) {
        this.userId = userId;
        this.status = OrderStatus.CREATED;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.couponIssueId = couponIssueId;
        guard();
        this.finalAmount = totalAmount.subtract(discountAmount);
    }

    @Override
    protected void guard() {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문자 정보는 필수입니다.");
        }
        if (totalAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 총액은 필수입니다.");
        }
        if (discountAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 필수입니다.");
        }
    }

    public Long userId() { return userId; }
    public OrderStatus status() { return status; }
    public Money totalAmount() { return totalAmount; }
    public Money discountAmount() { return discountAmount; }
    public Money finalAmount() { return finalAmount; }
    public Long couponIssueId() { return couponIssueId; }

    public void startPayment() {
        if (this.status != OrderStatus.CREATED && this.status != OrderStatus.PAYMENT_FAILED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제를 시작할 수 없는 주문 상태입니다.");
        }
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    public void confirmPayment() {
        if (this.status != OrderStatus.PAYMENT_PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 확인을 처리할 수 없는 주문 상태입니다.");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void failPayment() {
        if (this.status != OrderStatus.PAYMENT_PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 실패를 처리할 수 없는 주문 상태입니다.");
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 조회할 수 있습니다.");
        }
    }
}
