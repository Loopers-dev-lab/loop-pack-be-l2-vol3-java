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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "coupons")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponModel extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private CouponDiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    private CouponModel(String name, CouponDiscountType discountType, long discountValue,
                        Long minOrderAmount, int totalQuantity, ZonedDateTime expiredAt) {
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.totalQuantity = totalQuantity;
        this.expiredAt = expiredAt;
    }

    public static CouponModel create(String name, CouponDiscountType discountType,
                                     long discountValue, Long minOrderAmount,
                                     int totalQuantity, ZonedDateTime expiredAt) {
        validateName(name);
        validateDiscountValue(discountValue);
        validateRateRange(discountType, discountValue);
        validateTotalQuantity(totalQuantity);
        validateExpiredAt(expiredAt);
        return new CouponModel(name, discountType, discountValue,
                minOrderAmount, totalQuantity, expiredAt);
    }

    public void validateIssuable() {
        if (this.getDeletedAt() != null) {
            throw new CoreException(CouponErrorCode.ALREADY_DELETED);
        }
        if (this.expiredAt.isBefore(ZonedDateTime.now())) {
            throw new CoreException(CouponErrorCode.EXPIRED);
        }
    }

    public void update(String name, ZonedDateTime expiredAt) {
        validateName(name);
        this.name = name;
        this.expiredAt = expiredAt;
    }

    // === 검증 === //

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수값입니다.");
        }
    }

    private static void validateDiscountValue(long discountValue) {
        if (discountValue <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 1 이상이어야 합니다.");
        }
    }

    private static void validateRateRange(CouponDiscountType discountType, long discountValue) {
        if (discountType == CouponDiscountType.RATE && (discountValue < 1 || discountValue > 100)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 1~100 범위여야 합니다.");
        }
    }

    private static void validateTotalQuantity(int totalQuantity) {
        if (totalQuantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "총 수량은 1 이상이어야 합니다.");
        }
    }

    private static void validateExpiredAt(ZonedDateTime expiredAt) {
        if (expiredAt.isBefore(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 현재 시점 이후여야 합니다.");
        }
    }
}
