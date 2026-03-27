package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDateTime;
import java.util.UUID;

public record Coupon(
        UUID id,
        String name,
        CouponType type,
        int value,
        int minOrderAmount,
        int totalQuantity,
        int remainingQuantity,
        LocalDateTime expiredAt
) {
    public Coupon(String name, CouponType type, int value, int minOrderAmount, int totalQuantity, LocalDateTime expiredAt) {
        this(null, name, type, value, minOrderAmount, totalQuantity, totalQuantity, expiredAt);
    }

    public Coupon {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 값은 1 이상이어야 합니다.");
        }
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰 값은 100 이하여야 합니다.");
        }
        if (minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        if (totalQuantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 수량은 1 이상이어야 합니다.");
        }
        if (remainingQuantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "남은 쿠폰 수량은 0 이상이어야 합니다.");
        }
        if (remainingQuantity > totalQuantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "남은 쿠폰 수량은 총 수량보다 클 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 필수입니다.");
        }
    }

    public int calculateDiscount(int orderAmount) {
        if (orderAmount < minOrderAmount) {
            return 0;
        }
        if (type == CouponType.FIXED) {
            return Math.min(value, orderAmount);
        }
        return orderAmount * value / 100;
    }

    public boolean isUsableAt(LocalDateTime now) {
        return !now.isAfter(expiredAt);
    }

    public Coupon update(String name, CouponType type, int value, int minOrderAmount, int totalQuantity, LocalDateTime expiredAt) {
        int issuedCount = this.totalQuantity - this.remainingQuantity;
        int recalculatedRemainingQuantity = Math.max(0, totalQuantity - issuedCount);
        return new Coupon(id, name, type, value, minOrderAmount, totalQuantity, recalculatedRemainingQuantity, expiredAt);
    }
}
