package com.loopers.domain.coupon;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 도메인의 핵심 비즈니스 규칙을 담당하는 도메인 서비스.
 */
@DomainService
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    /**
     * 새로운 쿠폰을 생성한다.
     *
     * @param terms 쿠폰 생성 조건
     * @return 생성된 쿠폰
     */
    @Transactional
    public Coupon create(CouponTerms terms) {
        Coupon coupon = Coupon.create(terms);
        return couponRepository.save(coupon);
    }

    /**
     * 쿠폰 발급 수량을 차감한다.
     *
     * @param couponId 발급할 쿠폰 ID
     * @return 수량이 차감된 쿠폰
     * @throws CoreException 쿠폰이 존재하지 않거나 수량이 소진된 경우
     */
    @Transactional
    public Coupon issue(Long couponId) {
        Coupon coupon = couponRepository.findByIdAndDeletedAtIsNull(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        coupon.issue();
        return coupon;
    }

    /**
     * 쿠폰 정보를 수정한다.
     *
     * @param coupon 쿠폰 수정 정보 (couponId 포함)
     * @return 수정된 쿠폰
     * @throws CoreException 쿠폰이 존재하지 않는 경우
     */
    @Transactional
    public Coupon update(ModifyCoupon coupon) {
        Coupon entity = couponRepository.findById(coupon.couponId())
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        entity.update(coupon);
        return entity;
    }

    /**
     * 쿠폰을 소프트 삭제한다.
     *
     * @param couponId 삭제할 쿠폰 ID
     * @return 실제로 삭제가 수행되었으면 true, 이미 삭제된 상태면 false
     * @throws CoreException 쿠폰이 존재하지 않는 경우
     */
    @Transactional
    public boolean delete(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        if (coupon.isDeleted()) {
            return false;
        }
        coupon.delete();
        return true;
    }
}
