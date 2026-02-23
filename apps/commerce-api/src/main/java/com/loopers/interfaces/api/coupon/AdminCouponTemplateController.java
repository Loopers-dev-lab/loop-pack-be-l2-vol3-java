package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.AdminCouponFacade;
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

    private final AdminCouponFacade adminCouponFacade;

    public AdminCouponTemplateController(AdminCouponFacade adminCouponFacade) {
        this.adminCouponFacade = adminCouponFacade;
    }

    @GetMapping
    @Override
    public ApiResponse<AdminCouponTemplateResponse.TemplateListResponse> getTemplates(
            @AuthAdmin String ldap,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminCouponFacade.TemplateListResult result = adminCouponFacade.getTemplates(page, size);

        List<AdminCouponTemplateResponse.TemplateDetail> details = result.templates().stream()
                .map(t -> new AdminCouponTemplateResponse.TemplateDetail(
                        t.id(), t.name(), t.description(),
                        t.discountType(), t.discountValue(),
                        t.maxDiscountAmount(), t.minOrderAmount(),
                        t.maxIssueCount(), t.maxIssueCountPerUser(),
                        t.validFrom(), t.validTo(),
                        t.status(), t.createdAt(), t.updatedAt()))
                .toList();

        return ApiResponse.success(new AdminCouponTemplateResponse.TemplateListResponse(
                details, result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @PostMapping
    @Override
    public ApiResponse<AdminCouponTemplateResponse.TemplateDetail> createTemplate(
            @AuthAdmin String ldap,
            @RequestBody AdminCouponTemplateRequest.CreateTemplateRequest request) {
        AdminCouponFacade.TemplateDetail result = adminCouponFacade.createTemplate(
                request.name(), request.description(),
                DiscountType.valueOf(request.discountType()),
                request.discountValue(), request.maxDiscountAmount(),
                request.minOrderAmount(), request.maxIssueCount(),
                request.maxIssueCountPerUser(), request.validFrom(), request.validTo());

        return ApiResponse.success(toResponse(result));
    }

    @PutMapping("/{templateId}")
    @Override
    public ApiResponse<AdminCouponTemplateResponse.TemplateDetail> updateTemplate(
            @AuthAdmin String ldap,
            @PathVariable Long templateId,
            @RequestBody AdminCouponTemplateRequest.UpdateTemplateRequest request) {
        AdminCouponFacade.TemplateDetail result = adminCouponFacade.updateTemplate(
                templateId, request.name(), request.description(),
                DiscountType.valueOf(request.discountType()),
                request.discountValue(), request.maxDiscountAmount(),
                request.minOrderAmount());

        return ApiResponse.success(toResponse(result));
    }

    @DeleteMapping("/{templateId}")
    @Override
    public ApiResponse<Void> deleteTemplate(
            @AuthAdmin String ldap,
            @PathVariable Long templateId) {
        adminCouponFacade.deleteTemplate(templateId);
        return ApiResponse.success(null);
    }

    private AdminCouponTemplateResponse.TemplateDetail toResponse(AdminCouponFacade.TemplateDetail t) {
        return new AdminCouponTemplateResponse.TemplateDetail(
                t.id(), t.name(), t.description(),
                t.discountType(), t.discountValue(),
                t.maxDiscountAmount(), t.minOrderAmount(),
                t.maxIssueCount(), t.maxIssueCountPerUser(),
                t.validFrom(), t.validTo(),
                t.status(), t.createdAt(), t.updatedAt());
    }
}
