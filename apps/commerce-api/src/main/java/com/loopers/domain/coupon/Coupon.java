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
@Table(name = "coupon")
public class Coupon extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private int value;

    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "max_issuable")
    private Integer maxIssuable;

    protected Coupon() {}

    public Coupon(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        this(name, type, value, minOrderAmount, expiredAt, null);
    }

    public Coupon(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt, Integer maxIssuable) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 비어있을 수 없습니다.");
        }
        if (type == CouponType.FIXED && value < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "FIXED 쿠폰의 할인 금액은 1 이상이어야 합니다.");
        }
        if (type == CouponType.RATE && (value < 1 || value > 100)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "RATE 쿠폰의 할인율은 1~100 사이여야 합니다.");
        }
        if (minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        if (expiredAt == null || !expiredAt.isAfter(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 일시는 현재 시각 이후여야 합니다.");
        }
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.maxIssuable = maxIssuable;
    }

    public void update(String name, int minOrderAmount, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.");
        }
        if (minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        this.name = name;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

    public String getName() { return name; }
    public CouponType getType() { return type; }
    public int getValue() { return value; }
    public int getMinOrderAmount() { return minOrderAmount; }
    public ZonedDateTime getExpiredAt() { return expiredAt; }
    public Integer getMaxIssuable() { return maxIssuable; }
}
