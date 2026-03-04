package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponService couponService;

    // Command

    @Transactional
    public CouponInfo registerCoupon(CouponCommand.Register command) {
        Coupon coupon = couponService.register(command);
        return CouponInfo.from(coupon);
    }

    @Transactional
    public CouponInfo updateCoupon(Long couponId, CouponCommand.Update command) {
        if (command.type() != null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 유형은 변경할 수 없습니다");
        }
        Coupon coupon = couponService.update(couponId, command);
        return CouponInfo.from(coupon);
    }
}
