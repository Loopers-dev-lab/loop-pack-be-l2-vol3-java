package com.loopers.domain.coupon.exception;

public class CouponNotAvailableException extends RuntimeException {

    public CouponNotAvailableException() {
        super("사용할 수 없는 쿠폰입니다.");
    }
}
