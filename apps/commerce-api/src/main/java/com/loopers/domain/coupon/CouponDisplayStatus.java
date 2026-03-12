package com.loopers.domain.coupon;

/**
 * 쿠폰 표시 상태.
 * API 응답에서 사용자에게 보여줄 상태.
 */
public enum CouponDisplayStatus {
    AVAILABLE,  // 사용 가능
    USED,       // 사용 완료
    EXPIRED     // 만료됨
}
