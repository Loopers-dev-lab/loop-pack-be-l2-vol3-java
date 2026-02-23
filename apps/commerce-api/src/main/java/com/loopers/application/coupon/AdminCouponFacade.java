package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountType;
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

    /** 쿠폰 템플릿 수정 */
    @Transactional
    public TemplateDetail updateTemplate(Long templateId, String name, String description,
                                          DiscountType discountType, int discountValue,
                                          Integer maxDiscountAmount, int minOrderAmount) {
        CouponTemplate template = couponService.updateTemplate(
                templateId, name, description, discountType, discountValue,
                maxDiscountAmount, minOrderAmount);
        return toDetail(template);
    }

    /** 쿠폰 템플릿 삭제 */
    @Transactional
    public void deleteTemplate(Long templateId) {
        couponService.deleteTemplate(templateId);
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
}
