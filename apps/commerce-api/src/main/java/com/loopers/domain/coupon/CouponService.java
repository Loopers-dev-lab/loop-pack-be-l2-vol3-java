package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

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
     * @param name             쿠폰명
     * @param type             쿠폰 타입 (정액/정률)
     * @param discountValue    할인 값
     * @param maxDiscountPrice 최대 할인 금액
     * @param minOrderPrice    최소 주문 금액
     * @param expiredAt        만료 일시
     * @return 생성된 쿠폰
     */
    @Transactional
    public Coupon create(
            String name,
            CouponType type,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {
        Coupon coupon = Coupon.create(name, type, discountValue, maxDiscountPrice, minOrderPrice, expiredAt);
        return couponRepository.save(coupon);
    }

    /**
     * 쿠폰 정보를 수정한다.
     *
     * @param couponId         수정할 쿠폰 ID
     * @param name             쿠폰명
     * @param discountValue    할인 값
     * @param maxDiscountPrice 최대 할인 금액
     * @param minOrderPrice    최소 주문 금액
     * @param expiredAt        만료 일시
     * @return 수정된 쿠폰
     * @throws CoreException 쿠폰이 존재하지 않는 경우
     */
    @Transactional
    public Coupon update(
            Long couponId,
            String name,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        coupon.update(name, discountValue, maxDiscountPrice, minOrderPrice, expiredAt);
        return coupon;
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
