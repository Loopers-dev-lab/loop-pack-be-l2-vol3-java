package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

@Entity
@Table(name = "coupons")
@Getter
public class Coupon extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CouponType type;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected Coupon() {
    }

    private Coupon(String name, String description, CouponType type, BigDecimal discountValue, ZonedDateTime expiredAt) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.discountValue = discountValue;
        this.expiredAt = expiredAt;
    }

    public static Coupon create(String name, String description, CouponType type, BigDecimal discountValue, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("쿠폰 이름은 필수입니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("쿠폰 타입은 필수입니다.");
        }
        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("할인 값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("정률 할인은 100%를 초과할 수 없습니다.");
        }
        if (expiredAt == null || !expiredAt.isAfter(ZonedDateTime.now())) {
            throw new IllegalArgumentException("만료일은 현재 시각 이후여야 합니다.");
        }
        return new Coupon(name, description, type, discountValue, expiredAt);
    }

    public void update(String name, String description, CouponType type, BigDecimal discountValue, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("쿠폰 이름은 필수입니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("쿠폰 타입은 필수입니다.");
        }
        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("할인 값은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("정률 할인은 100%를 초과할 수 없습니다.");
        }
        if (expiredAt == null || !expiredAt.isAfter(ZonedDateTime.now())) {
            throw new IllegalArgumentException("만료일은 현재 시각 이후여야 합니다.");
        }
        this.name = name;
        this.description = description;
        this.type = type;
        this.discountValue = discountValue;
        this.expiredAt = expiredAt;
    }

    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return switch (type) {
            case FIXED -> discountValue.min(orderAmount);
            case RATE -> orderAmount.multiply(discountValue)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
        };
    }
}
