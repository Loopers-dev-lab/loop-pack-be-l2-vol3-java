package com.loopers.domain.coupon;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * 보유 쿠폰 도메인 리포지토리 인터페이스.
 */
public interface OwnedCouponRepository {

    /**
     * 보유 쿠폰을 저장한다.
     *
     * @param ownedCoupon 저장할 보유 쿠폰
     * @return 저장된 보유 쿠폰
     */
    OwnedCoupon save(OwnedCoupon ownedCoupon);

    /**
     * ID로 보유 쿠폰을 쿠폰 정보와 함께 조회한다.
     *
     * <p>쿠폰({@link Coupon})을 페치 조인하여 함께 로딩한다.</p>
     *
     * @param id 보유 쿠폰 ID
     * @return 쿠폰 정보를 포함한 보유 쿠폰 (존재하지 않으면 빈 Optional)
     */
    Optional<OwnedCoupon> findByIdWithCoupon(Long id);

    /**
     * 특정 쿠폰의 발급 내역을 페이징 조회한다.
     *
     * @param couponId 쿠폰 ID
     * @param pageable 페이징 조건
     * @return 보유 쿠폰 슬라이스
     */
    Slice<OwnedCoupon> findAllByCouponId(Long couponId, Pageable pageable);

    /**
     * 특정 사용자의 보유 쿠폰 목록을 페이징 조회한다.
     *
     * @param userId   사용자 ID
     * @param pageable 페이징 조건
     * @return 보유 쿠폰 슬라이스
     */
    Slice<OwnedCoupon> findAllByUserId(Long userId, Pageable pageable);

    /**
     * 특정 쿠폰이 사용자에게 이미 발급되었는지 확인한다.
     *
     * @param couponId 쿠폰 ID
     * @param userId   사용자 ID
     * @return 이미 발급되었으면 true
     */
    boolean existsByCouponIdAndUserId(Long couponId, Long userId);
}
