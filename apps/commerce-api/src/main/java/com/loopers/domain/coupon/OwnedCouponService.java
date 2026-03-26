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

    private final OwnedCouponRepository ownedCouponRepository;
    private final CouponDiscountProvider couponDiscountProvider;

    /**
     * 보유 쿠폰을 생성한다.
     *
     * <p>쿠폰 수량 차감 없이 보유 쿠폰만 생성한다.
     * 수량 차감은 {@link CouponService#issue(Long)}에서 처리한다.</p>
     *
     * @param coupon 발급할 쿠폰 엔티티
     * @param userId 발급 대상 사용자 ID
     * @return 발급된 보유 쿠폰
     * @throws CoreException 이미 발급된 경우
     */
    @Transactional
    public OwnedCoupon issue(Coupon coupon, Long userId) {
        if (ownedCouponRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
            throw new CoreException(ErrorType.ALREADY_COUPON_ISSUED);
        }
        OwnedCoupon ownedCoupon = OwnedCoupon.create(coupon, userId);
        return ownedCouponRepository.save(ownedCoupon);
    }

    /**
     * 보유 쿠폰의 할인 금액을 계산한다.
     *
     * <p>쿠폰 소유자 검증, 최소 주문 금액 검증을 수행한 뒤 할인 금액을 반환한다.
     * 쿠폰 사용 처리는 주문 생성 이벤트 리스너에서 별도로 수행한다.</p>
     *
     * @param ownedCouponId 적용할 보유 쿠폰 ID
     * @param userId        사용자 ID
     * @param orderTotal    주문 총액
     * @return 쿠폰 할인 정보
     * @throws CoreException 보유 쿠폰이 존재하지 않거나 소유자가 아니거나 최소 주문 금액 미달인 경우
     */
    @Transactional(readOnly = true)
    public CouponDiscount calculateDiscount(Long ownedCouponId, Long userId, Money orderTotal) {
        if (ownedCouponId == null) {
            return CouponDiscount.NONE;
        }
        OwnedCoupon ownedCoupon = ownedCouponRepository.findByIdWithCoupon(ownedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.OWNED_COUPON_NOT_FOUND));
        Money discountAmount = ownedCoupon.calculateDiscount(userId, orderTotal, couponDiscountProvider);
        return new CouponDiscount(discountAmount, ownedCouponId);
    }

    /**
     * 보유 쿠폰을 사용 처리한다.
     *
     * @param ownedCouponId 사용할 보유 쿠폰 ID
     * @throws CoreException 보유 쿠폰이 존재하지 않는 경우
     */
    @Transactional
    public void use(Long ownedCouponId) {
        OwnedCoupon ownedCoupon = ownedCouponRepository.findByIdWithCoupon(ownedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.OWNED_COUPON_NOT_FOUND));
        ownedCoupon.use();
    }

    /**
     * 사용된 보유 쿠폰을 복원한다.
     *
     * @param ownedCouponId 복원할 보유 쿠폰 ID
     * @throws CoreException 보유 쿠폰이 존재하지 않는 경우
     */
    @Transactional
    public void restore(Long ownedCouponId) {
        OwnedCoupon ownedCoupon = ownedCouponRepository.findByIdWithCoupon(ownedCouponId)
                .orElseThrow(() -> new CoreException(ErrorType.OWNED_COUPON_NOT_FOUND));
        ownedCoupon.restore();
    }
}
