package com.loopers.domain.coupon;

/**
 * 선착순 쿠폰 발급 요청의 처리 상태.
 * user_coupons의 라이프사이클 상태(AVAILABLE/USED/EXPIRED)와는 별개의 관심사다.
 */
public enum CouponIssueStatus {
    PENDING,    // 발급 요청 접수, Consumer 처리 대기 중
    ISSUED,     // 발급 성공 (user_coupons에 INSERT 완료)
    SOLD_OUT,   // 매진 (maxIssueCount 도달)
    REJECTED    // 거절 (중복 발급, 만료 등 비즈니스 거절)
}
