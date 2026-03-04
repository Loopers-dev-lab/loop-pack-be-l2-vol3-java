package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IssuedCouponService {

    private final IssuedCouponRepository issuedCouponRepository;

    // Command

    @Transactional
    public void deleteAvailableByCouponId(Long couponId) {
        List<IssuedCoupon> issuedCoupons = issuedCouponRepository.findAllByCouponId(couponId);
        issuedCoupons.stream()
                .filter(ic -> !ic.isUsed())
                .forEach(IssuedCoupon::delete);
    }
}
