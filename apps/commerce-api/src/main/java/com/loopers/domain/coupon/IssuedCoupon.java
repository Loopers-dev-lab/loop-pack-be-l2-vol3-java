package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "issued_coupons")
public class IssuedCoupon extends BaseEntity {

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IssuedCouponStatus status;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    protected IssuedCoupon() {}

    private IssuedCoupon(Long couponTemplateId, Long userId) {
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = IssuedCouponStatus.ISSUED;
    }

    public static IssuedCoupon create(Long couponTemplateId, Long userId) {
        return new IssuedCoupon(couponTemplateId, userId);
    }

    public void use(Long orderId) {
        if (this.status != IssuedCouponStatus.ISSUED) {
            throw new CoreException(CouponErrorType.INVALID_COUPON_STATUS);
        }
        this.status = IssuedCouponStatus.USED;
        this.orderId = orderId;
        this.usedAt = ZonedDateTime.now();
    }

    public void expire() {
        this.status = IssuedCouponStatus.EXPIRED;
    }

    public void validateOwnership(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(CouponErrorType.NOT_OWNER);
        }
    }

    public Long getCouponTemplateId() {
        return this.couponTemplateId;
    }

    public Long getUserId() {
        return this.userId;
    }

    public IssuedCouponStatus getStatus() {
        return this.status;
    }

    public Long getOrderId() {
        return this.orderId;
    }

    public ZonedDateTime getUsedAt() {
        return this.usedAt;
    }
}
