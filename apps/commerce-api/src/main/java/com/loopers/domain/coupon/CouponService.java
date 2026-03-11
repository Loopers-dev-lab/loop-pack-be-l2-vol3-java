package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public Coupon issue(Long userId, CouponTemplate template) {
        Coupon coupon = Coupon.issue(userId, template);
        return couponRepository.save(coupon);
    }

    @Transactional(readOnly = true)
    public Coupon getById(Long id) {
        return couponRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Coupon.COUPON_NOT_FOUND));
    }

    /**
     * 주문에 쿠폰 적용
     * - 쿠폰 검증 (소유자, 사용 여부, 만료, 최소 주문 금액)
     * - 할인 금액 계산
     * - 쿠폰 사용 처리
     */
    @Transactional
    public CouponApplyResult applyToOrder(Long couponId, Long userId, Money orderAmount) {
        if (couponId == null) {
            return CouponApplyResult.none();
        }

        Coupon coupon = getById(couponId);
        coupon.validateUsable(userId, orderAmount, ZonedDateTime.now());
        Money discountAmount = coupon.calculateDiscount(orderAmount);
        use(coupon);

        return new CouponApplyResult(coupon.getId(), discountAmount);
    }

    @Transactional
    public Coupon use(Coupon coupon) {
        coupon.use();
        return couponRepository.update(coupon);
    }

    @Transactional(readOnly = true)
    public List<Coupon> findByUserId(Long userId) {
        return couponRepository.findByUserId(userId);
    }
}
