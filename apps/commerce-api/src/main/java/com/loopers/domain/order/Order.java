package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Column(name = "user_coupon_id")
    private Long userCouponId;

    @Column(name = "discount_amount", nullable = false)
    private long discountAmount = 0;

    @Column(name = "final_price", nullable = false)
    private long finalPrice;

    protected Order() {}

    public Order(Long memberId, Money totalAmount, OrderStatus status) {
        this(memberId, totalAmount, status, null, 0);
    }

    public Order(Long memberId, Money totalAmount, OrderStatus status, Long userCouponId, long discountAmount) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 비어있을 수 없습니다.");
        }
        if (totalAmount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 금액은 비어있을 수 없습니다.");
        }
        this.memberId = memberId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.userCouponId = userCouponId;
        this.discountAmount = discountAmount;
        this.finalPrice = totalAmount.getValue() - discountAmount;
    }

    public Long getMemberId() {
        return memberId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Long getTotalAmount() {
        return totalAmount.getValue();
    }

    public Long getUserCouponId() {
        return userCouponId;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    public long getFinalPrice() {
        return finalPrice;
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }
}
