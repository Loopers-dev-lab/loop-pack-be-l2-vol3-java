package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponAdminService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final IssuedCouponRepository issuedCouponRepository;

    @Transactional
    public CouponInfo.CouponTemplateInfo create(String name, CouponType type, long value,
                                                 Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplate template = new CouponTemplate(name, type, value, minOrderAmount, expiredAt);
        CouponTemplate saved = couponTemplateRepository.save(template);
        return CouponInfo.CouponTemplateInfo.from(saved);
    }

    @Transactional(readOnly = true)
    public CouponInfo.CouponTemplateInfo getTemplate(Long couponId) {
        CouponTemplate template = couponTemplateRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        return CouponInfo.CouponTemplateInfo.from(template);
    }

    @Transactional(readOnly = true)
    public List<CouponInfo.CouponTemplateInfo> getTemplates(int page, int size) {
        return couponTemplateRepository.findAll(page, size).stream()
            .map(CouponInfo.CouponTemplateInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public long getTemplateCount() {
        return couponTemplateRepository.count();
    }

    @Transactional
    public CouponInfo.CouponTemplateInfo update(Long couponId, String name, CouponType type, long value,
                                                 Long minOrderAmount, ZonedDateTime expiredAt) {
        CouponTemplate template = couponTemplateRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        template.update(name, type, value, minOrderAmount, expiredAt);
        return CouponInfo.CouponTemplateInfo.from(template);
    }

    @Transactional
    public void delete(Long couponId) {
        CouponTemplate template = couponTemplateRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));
        template.delete();
    }

    @Transactional(readOnly = true)
    public List<CouponInfo.IssuedCouponDetailInfo> getIssuedCoupons(Long couponId, int page, int size) {
        return issuedCouponRepository.findByCouponTemplateId(couponId, page, size).stream()
            .map(CouponInfo.IssuedCouponDetailInfo::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public long getIssuedCouponCount(Long couponId) {
        return issuedCouponRepository.countByCouponTemplateId(couponId);
    }
}
