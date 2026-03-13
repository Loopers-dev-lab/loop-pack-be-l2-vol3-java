package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public CouponInfo.IssuedCouponInfo issue(Long memberId, Long couponTemplateId) {
        CouponTemplate template = couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        if (template.isExpired()) {
            throw new CoreException(ErrorType.COUPON_EXPIRED);
        }

        if (issuedCouponRepository.existsByMemberIdAndCouponTemplateId(memberId, couponTemplateId)) {
            throw new CoreException(ErrorType.COUPON_ALREADY_ISSUED);
        }

        IssuedCoupon issuedCoupon = issuedCouponRepository.save(new IssuedCoupon(couponTemplateId, memberId));
        return CouponInfo.IssuedCouponInfo.of(issuedCoupon, template);
    }
}
