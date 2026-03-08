package com.loopers.domain.coupon;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * 쿠폰 도메인 리포지토리 인터페이스.
 */
public interface CouponRepository {

    /**
     * 쿠폰을 저장한다.
     *
     * @param coupon 저장할 쿠폰
     * @return 저장된 쿠폰
     */
    Coupon save(Coupon coupon);

    /**
     * ID로 쿠폰을 조회한다. 삭제 여부와 무관하게 조회한다.
     *
     * @param couponId 쿠폰 ID
     * @return 쿠폰 (존재하지 않으면 빈 Optional)
     */
    Optional<Coupon> findById(Long couponId);

    /**
     * ID로 활성 쿠폰을 조회한다.
     *
     * @param couponId 쿠폰 ID
     * @return 활성 쿠폰 (존재하지 않거나 삭제된 경우 빈 Optional)
     */
    Optional<Coupon> findByIdAndDeletedAtIsNull(Long couponId);

    /**
     * 전체 쿠폰을 페이징 조회한다.
     *
     * @param pageable 페이징 조건
     * @return 쿠폰 슬라이스
     */
    Slice<Coupon> findAllBy(Pageable pageable);
}
