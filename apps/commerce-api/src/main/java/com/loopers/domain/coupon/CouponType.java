package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 쿠폰 할인 타입.
 * FIXED: 정액 할인(원), RATE: 정률 할인(%).
 */
public enum CouponType {
    FIXED,
    RATE;

    public static CouponType from(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        try {
            return CouponType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 쿠폰 타입입니다: " + value);
        }
    }
}
