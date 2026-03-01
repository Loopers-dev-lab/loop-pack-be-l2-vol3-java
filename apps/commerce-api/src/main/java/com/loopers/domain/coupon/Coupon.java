package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private int value;

    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected Coupon() {}

    public Coupon(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public void changeDetails(String name, CouponType type, int value, int minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

    public Money calculateDiscount(Money orderAmount) {
        return switch (type) {
            case FIXED -> new Money(Math.min(value, orderAmount.amount()));
            case RATE -> new Money(orderAmount.amount() * value / 100);
        };
    }

    public void validateApplicable(Money orderAmount) {
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (orderAmount.amount() < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "최소 주문 금액(" + minOrderAmount + "원) 이상이어야 쿠폰을 적용할 수 있습니다.");
        }
    }

    public String getName() { return name; }
    public CouponType getType() { return type; }
    public int getValue() { return value; }
    public int getMinOrderAmount() { return minOrderAmount; }
    public ZonedDateTime getExpiredAt() { return expiredAt; }

    private void validate(String name, CouponType type, int value, ZonedDateTime expiredAt) {
        Assert.hasText(name, "쿠폰 이름은 필수입니다.");
        Assert.notNull(type, "쿠폰 타입은 필수입니다.");
        Assert.state(value > 0, "쿠폰 값은 0보다 커야 합니다.");
        Assert.notNull(expiredAt, "만료일은 필수입니다.");
    }
}
