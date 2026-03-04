package com.loopers.domain.coupon.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record CouponName(String value) {

    public CouponName {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다.");
        }
        if (value.length() > 50) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 50자 이하여야 합니다.");
        }
    }
}
