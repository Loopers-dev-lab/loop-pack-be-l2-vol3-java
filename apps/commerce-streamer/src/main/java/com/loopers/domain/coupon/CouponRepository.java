package com.loopers.domain.coupon;

import java.util.Optional;

/**
 * 쿠폰 레포지토리 인터페이스 (Streamer 도메인 레이어).
 */
public interface CouponRepository {

    /**
     * CAS 기반 발급 카운트 증가.
     * issued_count < max_quantity 조건에서만 +1 한다.
     *
     * @return 업데이트된 행 수 (0이면 실패)
     */
    int incrementIssuedCountWithCas(Long couponId);

    Optional<CouponModel> findById(Long couponId);
}
