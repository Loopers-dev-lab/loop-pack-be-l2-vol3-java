package com.loopers.domain.coupon;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.coupon.discount.CouponDiscount;
import com.loopers.domain.coupon.discount.CouponDiscountProvider;
import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 보유 쿠폰 도메인의 핵심 비즈니스 규칙을 담당하는 도메인 서비스.
 */
@DomainService
@RequiredArgsConstructor
public class OwnedCouponService {

    private final CouponRepository couponRepository;
    private final OwnedCouponRepository ownedCouponRepository;
    private final CouponDiscountProvider couponDiscountProvider;

    /**
     * 사용자에게 쿠폰을 발급한다.
     *
     * @param couponId 발급할 쿠폰 ID
     * @param userId   발급 대상 사용자 ID
     * @return 발급된 보유 쿠폰
     * @throws CoreException 쿠폰이 존재하지 않거나 이미 발급된 경우
     */
    @Transactional
    public OwnedCoupon issue(Long couponId, Long userId) {
        Coupon coupon = couponRepository.findByIdAndDeletedAtIsNull(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        if (ownedCouponRepository.existsByCouponIdAndUserId(couponId, userId)) {
            throw new CoreException(ErrorType.ALREADY_COUPON_ISSUED);
        }
        OwnedCoupon ownedCoupon = OwnedCoupon.create(coupon, userId);
        return ownedCouponRepository.save(ownedCoupon);
    }

    /**
     * 보유 쿠폰을 주문에 적용하고 할인 금액을 계산한다.
     *
     * <p>쿠폰 소유자 검증, 최소 주문 금액 검증을 수행한 뒤 쿠폰을 사용 처리하고 할인 금액을 반환한다.</p>
     *
     * @param ownedCouponId 적용할 보유 쿠폰 ID
     * @param userId        사용자 ID
     * @param orderTotal    주문 총액
     * @return 쿠폰 할인 정보
     * @throws CoreException 보유 쿠폰이 존재하지 않거나 소유자가 아니거나 최소 주문 금액 미달인 경우
     */
    @Transactional
    public CouponDiscount applyCoupon(Long ownedCouponId, Long userId, Money orderTotal) {
        if (ownedCouponId == null) {
            return CouponDiscount.NONE;
        }

        OwnedCoupon ownedCoupon = ownedCouponRepository.findByIdWithCoupon(ownedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.OWNED_COUPON_NOT_FOUND));
        ownedCoupon.validateOwner(userId);
        Coupon coupon = ownedCoupon.getCoupon();
        coupon.validateMinOrderPrice(orderTotal);
        ownedCoupon.use();

        Money discountAmount = coupon.calculateDiscount(orderTotal, couponDiscountProvider);
        return new CouponDiscount(discountAmount, ownedCouponId);
    }
}
