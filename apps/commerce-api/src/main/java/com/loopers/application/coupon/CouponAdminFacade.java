package com.loopers.application.coupon;

import com.loopers.application.coupon.dto.FindCouponTemplateResDto;
import com.loopers.application.coupon.dto.FindIssuedCouponResDto;
import com.loopers.domain.coupon.model.CouponCommand;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponAdminFacade {

    private final CouponService couponService;

    @Transactional(rollbackFor = Exception.class)
    public FindCouponTemplateResDto createTemplate(CouponCommand.CreateTemplate command) {
        CouponTemplate template = couponService.createTemplate(command);
        return FindCouponTemplateResDto.from(template);
    }

    public FindCouponTemplateResDto getTemplate(Long templateId) {
        CouponTemplate template = couponService.getTemplate(templateId);
        return FindCouponTemplateResDto.from(template);
    }

    public Page<FindCouponTemplateResDto> getTemplates(Pageable pageable) {
        Page<CouponTemplate> templates = couponService.getTemplates(pageable);
        return templates.map(FindCouponTemplateResDto::from);
    }

    @Transactional(rollbackFor = Exception.class)
    public FindCouponTemplateResDto updateTemplate(Long templateId, CouponCommand.UpdateTemplate command) {
        CouponTemplate template = couponService.updateTemplate(templateId, command);
        return FindCouponTemplateResDto.from(template);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTemplate(Long templateId) {
        couponService.deleteTemplate(templateId);
    }

    public Page<FindIssuedCouponResDto> getIssuedCoupons(Long couponTemplateId, Pageable pageable) {
        Page<UserCoupon> issuedCoupons = couponService.getIssuedCoupons(couponTemplateId, pageable);
        return issuedCoupons.map(FindIssuedCouponResDto::from);
    }
}
