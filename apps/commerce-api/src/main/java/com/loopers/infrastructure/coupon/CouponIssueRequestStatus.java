package com.loopers.infrastructure.coupon;

public enum CouponIssueRequestStatus {
    PENDING,    // 요청 접수, Kafka 발행 대기
    ISSUED,     // 발급 완료
    FAILED      // 발급 실패 (재고 소진, 중복 발급 등)
}
