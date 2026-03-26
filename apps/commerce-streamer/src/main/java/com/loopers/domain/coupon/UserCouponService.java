package com.loopers.domain.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserCouponService {

    private final UserCouponRepository userCouponRepository;

    @Transactional(readOnly = true)
    public boolean existsByCouponIdAndMemberId(Long couponId, Long memberId) {
        return userCouponRepository.existsByCouponIdAndMemberId(couponId, memberId);
    }

    @Transactional
    public void issue(Long couponId, Long memberId) {
        userCouponRepository.save(new UserCouponModel(couponId, memberId));
    }
}
