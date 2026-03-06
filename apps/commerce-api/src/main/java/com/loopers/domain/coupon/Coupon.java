package com.loopers.domain.coupon;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_value", nullable = false))
    private Money discountValue;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "min_order_amount", nullable = false))
    private Money minOrderAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "max_discount_amount"))
    private Money maxDiscountAmount;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "issued_quantity", nullable = false)
    private int issuedQuantity;

    @Column(name = "valid_from", nullable = false)
    private ZonedDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private ZonedDateTime validUntil;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    private Coupon(String name, DiscountType discountType, Money discountValue,
                   Money minOrderAmount, Money maxDiscountAmount,
                   int totalQuantity, ZonedDateTime validFrom, ZonedDateTime validUntil) {
        validateName(name);
        validateDiscountValue(discountType, discountValue);
        validateQuantity(totalQuantity);
        validatePeriod(validFrom, validUntil);
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount != null ? minOrderAmount : Money.zero();
        this.maxDiscountAmount = maxDiscountAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.deleted = false;
    }

    public static Coupon create(String name, DiscountType discountType, Money discountValue,
                                Money minOrderAmount, Money maxDiscountAmount,
                                int totalQuantity, ZonedDateTime validFrom, ZonedDateTime validUntil) {
        return new Coupon(name, discountType, discountValue, minOrderAmount, maxDiscountAmount,
                totalQuantity, validFrom, validUntil);
    }

    public void issue() {
        validateIssuable();
        this.issuedQuantity++;
    }

    public void validateIssuable() {
        ZonedDateTime now = ZonedDateTime.now();
        if (now.isBefore(validFrom) || now.isAfter(validUntil)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 기간이 아닙니다.");
        }
        if (this.issuedQuantity >= this.totalQuantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 수량이 초과되었습니다.");
        }
        if (this.deleted) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 쿠폰입니다.");
        }
    }

    public Money calculateDiscount(Money orderAmount) {
        Money discount;
        if (this.discountType == DiscountType.FIXED) {
            discount = this.discountValue;
        } else {
            discount = orderAmount.percentage((int) this.discountValue.getAmount().longValue());
            if (this.maxDiscountAmount != null) {
                discount = discount.min(this.maxDiscountAmount);
            }
        }
        return discount.min(orderAmount);
    }

    public void validateUsable(Money orderAmount) {
        ZonedDateTime now = ZonedDateTime.now();
        if (now.isBefore(validFrom) || now.isAfter(validUntil)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 사용 기간이 아닙니다.");
        }
        if (this.minOrderAmount.isGreaterThan(orderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다.");
        }
    }

    public void update(String name, DiscountType discountType, Money discountValue,
                       Money minOrderAmount, Money maxDiscountAmount,
                       int totalQuantity, ZonedDateTime validFrom, ZonedDateTime validUntil) {
        validateName(name);
        validateDiscountValue(discountType, discountValue);
        validateQuantity(totalQuantity);
        validatePeriod(validFrom, validUntil);
        if (totalQuantity < this.issuedQuantity) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "총 수량은 이미 발급된 수량(" + this.issuedQuantity + ")보다 작을 수 없습니다.");
        }
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount != null ? minOrderAmount : Money.zero();
        this.maxDiscountAmount = maxDiscountAmount;
        this.totalQuantity = totalQuantity;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
    }

    @Override
    public void delete() {
        this.deleted = true;
    }

    @Override
    public void restore() {
        this.deleted = false;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수입니다.");
        }
    }

    private void validateDiscountValue(DiscountType discountType, Money discountValue) {
        if (discountValue == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 필수입니다.");
        }
        if (discountType == DiscountType.RATE) {
            long rate = discountValue.getAmount().longValue();
            if (rate < 1 || rate > 100) {
                throw new CoreException(ErrorType.BAD_REQUEST, "할인율은 1~100 사이여야 합니다.");
            }
        }
    }

    private void validateQuantity(int totalQuantity) {
        if (totalQuantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 발급 수량은 1 이상이어야 합니다.");
        }
    }

    private void validatePeriod(ZonedDateTime validFrom, ZonedDateTime validUntil) {
        if (validFrom == null || validUntil == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효 기간은 필수입니다.");
        }
        if (validFrom.isAfter(validUntil)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "시작일은 종료일보다 이전이어야 합니다.");
        }
    }
}
