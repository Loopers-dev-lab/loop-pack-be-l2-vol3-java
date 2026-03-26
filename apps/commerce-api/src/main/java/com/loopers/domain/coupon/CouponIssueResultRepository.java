package com.loopers.domain.coupon;

import java.util.Optional;

/**
 * 쿠폰 발급 결과 레포지토리 인터페이스 (도메인 레이어).
 */
public interface CouponIssueResultRepository {

    Optional<CouponIssueResultModel> findById(String requestId);
}
