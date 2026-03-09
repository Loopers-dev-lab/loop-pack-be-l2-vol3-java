package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * 쿠폰 유스케이스 조율.
 * 대고객: 발급, 내 쿠폰 목록. 어드민: 템플릿 CRUD, 발급 내역.
 */
@Service
public class CouponFacade {

    private final CouponService couponService;

    public CouponFacade(CouponService couponService) {
        this.couponService = couponService;
    }

    @Transactional
    public IssuedCouponInfo issueCoupon(Long userId, Long couponTemplateId) {
        var issued = couponService.issue(userId, couponTemplateId);
        return IssuedCouponInfo.from(issued);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> getMyCoupons(Long userId, Pageable pageable) {
        return couponService.findByUserIdAsProjection(userId, pageable).map(IssuedCouponInfo::from);
    }

    @Transactional(readOnly = true)
    public Page<CouponTemplateInfo> getTemplates(Pageable pageable) {
        return couponService.findTemplatesNotDeletedAsProjection(pageable).map(CouponTemplateInfo::from);
    }

    @Transactional(readOnly = true)
    public CouponTemplateInfo getTemplate(Long couponId) {
        return couponService.findTemplateByIdAndNotDeletedAsProjection(couponId)
                .map(CouponTemplateInfo::from)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    }

    @Transactional
    public CouponTemplateInfo registerTemplate(String name, String typeValue, int value,
                                            BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        CouponType type = CouponType.from(typeValue);
        var template = CouponTemplateModel.create(name, type, value, minOrderAmount, expiredAt);
        var saved = couponService.persistTemplate(template);
        return CouponTemplateInfo.from(saved);
    }

    @Transactional
    public CouponTemplateInfo updateTemplate(Long couponId, String name, String typeValue, int value,
                                            BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplateModel template = couponService.findTemplateByIdAndNotDeleted(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        CouponType type = CouponType.from(typeValue);
        template.update(name, type, value, minOrderAmount, expiredAt);
        var saved = couponService.persistTemplate(template);
        return CouponTemplateInfo.from(saved);
    }

    @Transactional
    public void deleteTemplate(Long couponId) {
        CouponTemplateModel template = couponService.findTemplateByIdAndNotDeleted(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
        template.delete();
        couponService.persistTemplate(template);
    }

    @Transactional(readOnly = true)
    public Page<IssuedCouponInfo> getIssueHistory(Long couponId, Pageable pageable) {
        return couponService.findByCouponIdAsProjection(couponId, pageable).map(IssuedCouponInfo::from);
    }
}
