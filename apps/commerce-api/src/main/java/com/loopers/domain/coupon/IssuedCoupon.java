package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "issued_coupons")
@Getter
public class IssuedCoupon extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_name", nullable = false)
    private String couponName;

    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false)
    private CouponType couponType;

    @Column(name = "coupon_value", nullable = false)
    private int couponValue;

    @Column(name = "min_order_amount")
    private BigDecimal minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    protected IssuedCoupon() {}

    private IssuedCoupon(Long couponId, Long userId, String couponName,
                         CouponType couponType, int couponValue,
                         BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        this.couponId = couponId;
        this.userId = userId;
        this.couponName = couponName;
        this.couponType = couponType;
        this.couponValue = couponValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static IssuedCoupon create(Long couponId, Long userId, String couponName,
                                       CouponType couponType, int couponValue,
                                       BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        return new IssuedCoupon(couponId, userId, couponName, couponType, couponValue,
                minOrderAmount, expiredAt);
    }

    public void use() {
        if (isUsed()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다");
        }
        this.usedAt = LocalDateTime.now();
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    public boolean isExpired() {
        return expiredAt.isBefore(LocalDateTime.now());
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public BigDecimal calculateDiscount(BigDecimal totalAmount) {
        return switch (couponType) {
            case FIXED -> totalAmount.min(BigDecimal.valueOf(couponValue));
            case RATE -> totalAmount.multiply(BigDecimal.valueOf(couponValue))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        };
    }

    public void validateUsable() {
        if (isDeleted()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다");
        }
        if (isUsed()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다");
        }
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다");
        }
    }

    public void validateMinOrderAmount(BigDecimal totalAmount) {
        if (minOrderAmount != null && totalAmount.compareTo(minOrderAmount) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액 조건을 충족하지 않습니다");
        }
    }
}
