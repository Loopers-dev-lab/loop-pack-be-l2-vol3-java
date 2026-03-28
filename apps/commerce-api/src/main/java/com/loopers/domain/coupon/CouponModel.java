package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Percentage;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon")
@Getter
public class CouponModel extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponType type;

    private Money discountAmount;
    private Integer discountRate;
    private Money minOrderAmount;

    @Column(nullable = false)
    private ZonedDateTime expiredAt;

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int issuedQuantity;

    protected CouponModel() {
    }

    public CouponModel(String name, CouponType type, BigDecimal discountAmount,
                       Integer discountRate, BigDecimal minOrderAmount, ZonedDateTime expiredAt, int totalQuantity) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수입니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (type == CouponType.FIXED && (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정액 할인 금액은 0보다 커야 합니다.");
        }
        if (type == CouponType.RATE && (discountRate == null || discountRate < 1 || discountRate > 100)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인율은 1 이상 100 이하여야 합니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }

        this.name = name;
        this.type = type;
        this.discountAmount = discountAmount != null ? new Money(discountAmount) : null;
        this.discountRate = discountRate;
        this.minOrderAmount = minOrderAmount != null ? new Money(minOrderAmount) : null;
        this.expiredAt = expiredAt;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
    }

    public void modifyInfo(String name, BigDecimal value,
                          BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수입니다.");
        }
        if (this.type == CouponType.FIXED && (value == null || value.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정액 할인 금액은 0보다 커야 합니다.");
        }
        if (this.type == CouponType.RATE && (value == null || value.intValue() < 1 || value.intValue() > 100)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인율은 1 이상 100 이하여야 합니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
        this.name = name;
        if (this.type == CouponType.FIXED) {
            this.discountAmount = new Money(value);
        } else {
            this.discountRate = value.intValue();
        }
        this.minOrderAmount = minOrderAmount != null ? new Money(minOrderAmount) : null;
        this.expiredAt = expiredAt;
    }

    public Money calculateDiscount(Money orderAmount) {
        if (type == CouponType.FIXED) {
            return orderAmount.isGreaterThanOrEqual(this.discountAmount) ? this.discountAmount : orderAmount;
        }
        Percentage rate = new Percentage(this.discountRate);
        return rate.calculateDiscount(orderAmount);
    }

    public void validateNotExpired() {
        if (this.expiredAt.isBefore(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
    }

    public void validateMinOrderAmount(Money orderAmount) {
        if (this.minOrderAmount != null && !orderAmount.isGreaterThanOrEqual(this.minOrderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액 조건을 충족하지 않습니다.");
        }
    }

    public int getRemainingQuantity() {
        if (totalQuantity == 0) return Integer.MAX_VALUE;
        return totalQuantity - issuedQuantity;
    }

    public void increaseIssuedQuantity() {
        if (totalQuantity > 0 && getRemainingQuantity() <= 0) {
            throw new CoreException(ErrorType.CONFLICT, "쿠폰이 모두 소진되었습니다.");
        }
        this.issuedQuantity++;
    }
}
