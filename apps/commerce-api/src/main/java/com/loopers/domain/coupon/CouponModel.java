package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon")
public class CouponModel extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private int value;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "min_order_amount"))
    private Money minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "max_quantity")
    private Integer maxQuantity;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    protected CouponModel() {
    }

    public CouponModel(String name, CouponType type, int value, Money minOrderAmount, LocalDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.issuedCount = 0;
        guard();
    }

    public CouponModel(String name, CouponType type, int value, Money minOrderAmount,
                        LocalDateTime expiredAt, Integer maxQuantity) {
        this(name, type, value, minOrderAmount, expiredAt);
        this.maxQuantity = maxQuantity;
    }

    @Override
    protected void guard() {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 값은 0보다 커야 합니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
    }

    public Money calculateDiscount(Money orderAmount) {
        return this.type.calculateDiscount(this.value, orderAmount);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiredAt);
    }

    public boolean meetsMinOrderAmount(Money orderAmount) {
        if (this.minOrderAmount == null) {
            return true;
        }
        return orderAmount.value() >= this.minOrderAmount.value();
    }

    public void validateUsable(Money orderAmount) {
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (!meetsMinOrderAmount(orderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액 조건을 충족하지 않습니다.");
        }
    }

    public boolean hasQuantityLimit() {
        return maxQuantity != null;
    }

    public boolean isQuantityAvailable() {
        if (!hasQuantityLimit()) return true;
        return issuedCount < maxQuantity;
    }

    public void incrementIssuedCount() {
        if (hasQuantityLimit() && issuedCount >= maxQuantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 수량이 초과되었습니다.");
        }
        this.issuedCount++;
    }

    public void update(String name, CouponType type, int value, Money minOrderAmount, LocalDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        guard();
    }

    public String name() {
        return name;
    }

    public CouponType type() {
        return type;
    }

    public int value() {
        return value;
    }

    public Money minOrderAmount() {
        return minOrderAmount;
    }

    public LocalDateTime expiredAt() {
        return expiredAt;
    }

    public Integer maxQuantity() {
        return maxQuantity;
    }

    public int issuedCount() {
        return issuedCount;
    }
}
