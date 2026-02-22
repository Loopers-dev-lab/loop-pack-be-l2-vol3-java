package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthAdmin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/coupon-templates")
public class AdminCouponTemplateController implements AdminCouponTemplateApiSpec {

    private final CouponService couponService;

    public AdminCouponTemplateController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping
    @Override
    public ApiResponse<AdminCouponTemplateResponse.TemplateListResponse> getTemplates(
            @AuthAdmin String ldap,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<CouponTemplate> templates = couponService.getAllTemplates(page, size);
        long totalElements = couponService.countAllTemplates();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<AdminCouponTemplateResponse.TemplateDetail> details = templates.stream()
                .map(this::toDetail)
                .toList();

        return ApiResponse.success(new AdminCouponTemplateResponse.TemplateListResponse(
                details, page, size, totalElements, totalPages));
    }

    @PostMapping
    @Override
    public ApiResponse<AdminCouponTemplateResponse.TemplateDetail> createTemplate(
            @AuthAdmin String ldap,
            @RequestBody AdminCouponTemplateRequest.CreateTemplateRequest request) {
        CouponTemplate template = couponService.createTemplate(
                request.name(), request.description(),
                DiscountType.valueOf(request.discountType()),
                request.discountValue(), request.maxDiscountAmount(),
                request.minOrderAmount(), request.maxIssueCount(),
                request.maxIssueCountPerUser(), request.validFrom(), request.validTo());

        return ApiResponse.success(toDetail(template));
    }

    @PutMapping("/{templateId}")
    @Override
    public ApiResponse<AdminCouponTemplateResponse.TemplateDetail> updateTemplate(
            @AuthAdmin String ldap,
            @PathVariable Long templateId,
            @RequestBody AdminCouponTemplateRequest.UpdateTemplateRequest request) {
        CouponTemplate template = couponService.updateTemplate(
                templateId, request.name(), request.description(),
                DiscountType.valueOf(request.discountType()),
                request.discountValue(), request.maxDiscountAmount(),
                request.minOrderAmount());

        return ApiResponse.success(toDetail(template));
    }

    @DeleteMapping("/{templateId}")
    @Override
    public ApiResponse<Void> deleteTemplate(
            @AuthAdmin String ldap,
            @PathVariable Long templateId) {
        couponService.deleteTemplate(templateId);
        return ApiResponse.success(null);
    }

    private AdminCouponTemplateResponse.TemplateDetail toDetail(CouponTemplate t) {
        return new AdminCouponTemplateResponse.TemplateDetail(
                t.getId(), t.getName(), t.getDescription(),
                t.getDiscountType().name(), t.getDiscountValue(),
                t.getMaxDiscountAmount(), t.getMinOrderAmount(),
                t.getMaxIssueCount(), t.getMaxIssueCountPerUser(),
                t.getValidFrom(), t.getValidTo(),
                t.getStatus().name(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
