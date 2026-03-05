package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    private Coupon(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static Coupon create(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, expiredAt);
        return new Coupon(name, type, value, minOrderAmount, expiredAt);
    }

    public void update(String name, CouponType type, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        if (name != null) {
            if (name.isBlank()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수입니다.");
            }
            this.name = name;
        }
        if (type != null) {
            this.type = type;
        }
        if (value != null) {
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
            }
            this.value = value;
        }
        if (this.type == CouponType.RATE && this.value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비율 할인은 100을 초과할 수 없습니다.");
        }
        if (minOrderAmount != null) {
            this.minOrderAmount = minOrderAmount;
        }
        if (expiredAt != null) {
            this.expiredAt = expiredAt;
        }
    }

    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        if (type == CouponType.FIXED) {
            return value.compareTo(orderAmount) > 0 ? orderAmount : value;
        }
        return orderAmount.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
    }

    public void validateApplicable(BigDecimal orderAmount) {
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (minOrderAmount != null && orderAmount.compareTo(minOrderAmount) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다.");
        }
    }

    public boolean isExpired() {
        return expiredAt.isBefore(ZonedDateTime.now());
    }

    public boolean isDeleted() {
        return this.getDeletedAt() != null;
    }

    private static void validate(String name, CouponType type, BigDecimal value, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수입니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비율 할인은 100을 초과할 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
        if (expiredAt.isBefore(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 현재 시간 이후여야 합니다.");
        }
    }
}
