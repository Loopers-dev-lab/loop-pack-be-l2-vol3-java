package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 어드민 쿠폰 Facade
 *
 * CouponService를 위임하여 어드민 쿠폰 템플릿 관리 유스케이스를 처리한다.
 */
@Component
public class AdminCouponFacade {

    private final CouponService couponService;

    public AdminCouponFacade(CouponService couponService) {
        this.couponService = couponService;
    }

    /** 쿠폰 템플릿 목록 조회 */
    @Transactional(readOnly = true)
    public TemplateListResult getTemplates(int page, int size) {
        List<CouponTemplate> templates = couponService.getAllTemplates(page, size);
        long totalElements = couponService.countAllTemplates();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<TemplateDetail> details = templates.stream()
                .map(this::toDetail)
                .toList();

        return new TemplateListResult(details, page, size, totalElements, totalPages);
    }

    /** 쿠폰 템플릿 생성 */
    @Transactional
    public TemplateDetail createTemplate(String name, String description, DiscountType discountType,
                                          int discountValue, Integer maxDiscountAmount, int minOrderAmount,
                                          int maxIssueCount, int maxIssueCountPerUser,
                                          ZonedDateTime validFrom, ZonedDateTime validTo) {
        CouponTemplate template = couponService.createTemplate(
                name, description, discountType, discountValue, maxDiscountAmount,
                minOrderAmount, maxIssueCount, maxIssueCountPerUser, validFrom, validTo);
        return toDetail(template);
    }

    /** 쿠폰 템플릿 부분 수정 */
    @Transactional
    public TemplateDetail updateTemplate(Long templateId, String name, String description,
                                          DiscountType discountType, Integer discountValue,
                                          Integer maxDiscountAmount, Integer minOrderAmount) {
        CouponTemplate template = couponService.updateTemplate(
                templateId, name, description, discountType, discountValue,
                maxDiscountAmount, minOrderAmount);
        return toDetail(template);
    }

    /** 쿠폰 템플릿 상세 조회 */
    @Transactional(readOnly = true)
    public TemplateDetail getTemplateDetail(Long templateId) {
        CouponTemplate template = couponService.getTemplate(templateId);
        return toDetail(template);
    }

    /** 쿠폰 템플릿 삭제 */
    @Transactional
    public void deleteTemplate(Long templateId) {
        couponService.deleteTemplate(templateId);
    }

    /** 특정 쿠폰의 발급 내역 조회 (in-memory 페이징, 음수 page/size 방어) */
    @Transactional(readOnly = true)
    public IssuedCouponListResult getIssuedCoupons(Long templateId, int page, int size) {
        couponService.getTemplate(templateId);
        List<IssuedCoupon> issuedCoupons = couponService.getIssuedCouponsByTemplateId(templateId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        int start = Math.min(safePage * safeSize, issuedCoupons.size());
        int end = Math.min(start + safeSize, issuedCoupons.size());
        List<IssuedCouponSummary> summaries = issuedCoupons.subList(start, end).stream()
                .map(c -> new IssuedCouponSummary(
                        c.getId(), c.getUserId(), c.getStatus().name(),
                        c.getOrderId(), c.getUsedAt(), c.getCreatedAt()))
                .toList();
        return new IssuedCouponListResult(summaries, safePage, safeSize, issuedCoupons.size(),
                (int) Math.ceil((double) issuedCoupons.size() / safeSize));
    }

    private TemplateDetail toDetail(CouponTemplate t) {
        return new TemplateDetail(
                t.getId(), t.getName(), t.getDescription(),
                t.getDiscountType().name(), t.getDiscountValue(),
                t.getMaxDiscountAmount(), t.getMinOrderAmount(),
                t.getMaxIssueCount(), t.getMaxIssueCountPerUser(),
                t.getValidFrom(), t.getValidTo(),
                t.getStatus().name(), t.getCreatedAt(), t.getUpdatedAt());
    }

    public record TemplateDetail(
            Long id, String name, String description,
            String discountType, int discountValue,
            Integer maxDiscountAmount, int minOrderAmount,
            int maxIssueCount, int maxIssueCountPerUser,
            ZonedDateTime validFrom, ZonedDateTime validTo,
            String status, ZonedDateTime createdAt, ZonedDateTime updatedAt) {}

    public record TemplateListResult(
            List<TemplateDetail> templates,
            int page, int size, long totalElements, int totalPages) {}

    public record IssuedCouponSummary(
            Long issuedCouponId, Long userId, String status,
            Long orderId, ZonedDateTime usedAt, ZonedDateTime createdAt) {}

    public record IssuedCouponListResult(
            List<IssuedCouponSummary> issuedCoupons,
            int page, int size, long totalElements, int totalPages) {}
}
