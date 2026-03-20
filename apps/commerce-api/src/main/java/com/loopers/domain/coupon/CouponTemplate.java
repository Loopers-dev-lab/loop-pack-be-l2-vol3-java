package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_template")
public class CouponTemplate extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private long value;

    private Long minOrderAmount;

    @Column(nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponTemplate() {}

    public CouponTemplate(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validateName(name);
        validateType(type);
        validateValue(type, value);
        validateExpiredAt(expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다.");
        }
    }

    private void validateType(CouponType type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
    }

    private void validateValue(CouponType type, long value) {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 할인율은 100%를 초과할 수 없습니다.");
        }
    }

    private void validateExpiredAt(ZonedDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 만료일은 필수입니다.");
        }
    }

    public long calculateDiscount(long orderAmount) {
        if (type == CouponType.FIXED) {
            return Math.min(value, orderAmount);
        }
        return orderAmount * value / 100;
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

    public void validateMinOrderAmount(long orderAmount) {
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.COUPON_MIN_ORDER_AMOUNT,
                "최소 주문 금액 " + minOrderAmount + "원 이상이어야 합니다.");
        }
    }

    public void update(String name, CouponType type, long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validateName(name);
        validateType(type);
        validateValue(type, value);
        validateExpiredAt(expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public String getName() { return name; }
    public CouponType getType() { return type; }
    public long getValue() { return value; }
    public Long getMinOrderAmount() { return minOrderAmount; }
    public ZonedDateTime getExpiredAt() { return expiredAt; }
}
