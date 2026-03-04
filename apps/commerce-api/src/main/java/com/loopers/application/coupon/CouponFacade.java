package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 쿠폰 Facade
 *
 * CouponService를 조합하여 사용자 쿠폰 관련 유스케이스를 처리한다.
 */
@Component
public class CouponFacade {

    private final CouponService couponService;

    public CouponFacade(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 쿠폰 발급 */
    @Transactional
    public IssueCouponResult issueCoupon(Long templateId, Long userId) {
        IssuedCoupon issued = couponService.issue(templateId, userId);
        return new IssueCouponResult(issued.getId(), issued.getStatus().name());
    }

    /** 내 쿠폰 목록 조회 */
    public CouponListResult getMyCoupons(Long userId) {
        List<IssuedCoupon> coupons = couponService.getUserCoupons(userId);

        Set<Long> templateIds = coupons.stream()
                .map(IssuedCoupon::getCouponTemplateId)
                .collect(Collectors.toSet());

        Map<Long, CouponTemplate> templateMap = couponService.getTemplatesByIds(templateIds).stream()
                .collect(Collectors.toMap(CouponTemplate::getId, Function.identity()));

        List<IssuedCouponDetail> details = coupons.stream()
                .map(c -> {
                    CouponTemplate t = templateMap.get(c.getCouponTemplateId());
                    return new IssuedCouponDetail(
                            c.getId(), c.getCouponTemplateId(),
                            t.getName(), t.getDiscountType().name(),
                            t.getDiscountValue(), t.getMaxDiscountAmount(),
                            c.getStatus().name(), c.getUsedAt(), c.getCreatedAt());
                })
                .toList();

        return new CouponListResult(details);
    }

    public record IssueCouponResult(Long issuedCouponId, String status) {}

    public record IssuedCouponDetail(
            Long issuedCouponId, Long couponTemplateId,
            String couponName, String discountType,
            int discountValue, Integer maxDiscountAmount,
            String status, ZonedDateTime usedAt, ZonedDateTime createdAt) {}

    public record CouponListResult(List<IssuedCouponDetail> coupons) {}
}
