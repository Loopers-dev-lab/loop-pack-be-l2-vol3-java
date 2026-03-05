package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class IssuedCouponService {

    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional(readOnly = true)
    public List<IssuedCouponInfo> getIssuedCoupons(Long userId) {
        return issuedCouponRepository.findByUserId(userId)
                                     .stream()
                                     .map(IssuedCouponInfo::from)
                                     .toList();
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> findByCouponId(Long couponId, Pageable pageable) {
        return issuedCouponRepository.findByCouponId(couponId, pageable)
                                     .map(IssuedCouponInfo::from);
    }
}
