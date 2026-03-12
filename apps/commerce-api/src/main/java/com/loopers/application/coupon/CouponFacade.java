package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;

    // 쿠폰 발급 (US-C01)
    @Transactional
    public UserCouponInfo issue(Long userId, Long couponTemplateId) {
        UserCoupon savedUserCoupon = couponService.issue(userId, couponTemplateId);
        return UserCouponInfo.from(savedUserCoupon, LocalDateTime.now());
    }

    /**
     * 내 쿠폰 목록 조회 (US-C02)
     * 쿠폰의 상태는 조회 시각 기준으로 계산한다 (BR-C04).
     * expiredAt은 UserCoupon에 스냅샷되어 있으므로 템플릿 조회 불필요.
     */
    @Transactional(readOnly = true)
    public List<UserCouponInfo> findMyIssuedCoupons(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return couponService.findAllIssuedByUserId(userId).stream()
                .map(userCoupon -> UserCouponInfo.from(userCoupon, now))
                .toList();
    }
}
