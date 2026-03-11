package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponFacade {

    private final CouponTemplateService couponTemplateService;
    private final CouponService couponService;

    @Transactional
    public CouponTemplateInfo createCouponTemplate(CreateCouponTemplateCommand command) {
        CouponTemplate template = couponTemplateService.create(
            command.name(), command.discountType(), command.discountValue(), command.minOrderAmount(), command.expiredAt()
        );
        return CouponTemplateInfo.from(template);
    }

    @Transactional
    public CouponTemplateInfo updateCouponTemplate(UpdateCouponTemplateCommand command) {
        CouponTemplate template = couponTemplateService.update(
            command.couponId(), command.name(), command.discountType(), command.discountValue(), command.minOrderAmount(), command.expiredAt()
        );
        return CouponTemplateInfo.from(template);
    }

    @Transactional
    public void deleteCouponTemplate(Long couponId) {
        couponTemplateService.delete(couponId);
    }

    @Transactional(readOnly = true)
    public CouponTemplateInfo getCouponTemplate(Long couponId) {
        CouponTemplate template = couponTemplateService.getById(couponId);
        return CouponTemplateInfo.from(template);
    }

    @Transactional(readOnly = true)
    public List<CouponTemplateInfo> getCouponTemplates(int page, int size) {
        return couponTemplateService.findAll(page, size).stream()
            .map(CouponTemplateInfo::from)
            .toList();
    }

    @Transactional
    public CouponInfo issueCoupon(Long userId, Long templateId) {
        CouponTemplate template = couponTemplateService.getById(templateId);
        Coupon coupon = couponService.issue(userId, template);
        return CouponInfo.from(coupon);
    }

    @Transactional(readOnly = true)
    public List<CouponInfo> getMyCoupons(Long userId) {
        return couponService.findByUserId(userId).stream()
            .map(CouponInfo::from)
            .toList();
    }
}
