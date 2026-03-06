package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public UserCoupon issue(Long userId, Long couponTemplateId) {
        CouponTemplate template = couponTemplateRepository.findById(couponTemplateId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다"));

        UserCoupon userCoupon = new UserCoupon(userId, couponTemplateId, template.getValidDays());
        return userCouponRepository.save(userCoupon);
    }

    @Transactional(readOnly = true)
    public List<UserCoupon> getUserCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public CouponTemplate getTemplate(Long id) {
        return couponTemplateRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다"));
    }

    @Transactional
    public CouponTemplate createTemplate(CouponTemplate template) {
        return couponTemplateRepository.save(template);
    }
}
