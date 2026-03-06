package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final IssuedCouponRepository issuedCouponRepository;
    private final CouponTemplateRepository couponTemplateRepository;

    public List<CouponInfo.IssuedCouponInfo> getMyCoupons(Long memberId) {
        List<IssuedCoupon> issuedCoupons = issuedCouponRepository.findByMemberId(memberId);
        return issuedCoupons.stream()
            .map(issued -> {
                CouponTemplate template = couponTemplateRepository.findById(issued.getCouponTemplateId())
                    .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
                return CouponInfo.IssuedCouponInfo.of(issued, template);
            })
            .toList();
    }
}
