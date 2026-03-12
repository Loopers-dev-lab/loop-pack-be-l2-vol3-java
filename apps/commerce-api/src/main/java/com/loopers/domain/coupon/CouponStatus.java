package com.loopers.domain.coupon;

public enum CouponStatus {
    AVAILABLE,  // 사용 가능
    USED,       // 사용 완료
    EXPIRED     // 만료 (조회 시 계산, DB 저장 안 함)
}
