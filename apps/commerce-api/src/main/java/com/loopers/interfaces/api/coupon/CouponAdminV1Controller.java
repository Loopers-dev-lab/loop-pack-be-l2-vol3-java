package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponAdminService;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponAdminService couponAdminService;

    @GetMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponTemplateListResponse> getTemplates(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<CouponAdminV1Dto.CouponTemplateResponse> coupons = couponAdminService.getTemplates(page, size).stream()
            .map(CouponAdminV1Dto.CouponTemplateResponse::from)
            .toList();
        long totalCount = couponAdminService.getTemplateCount();
        return ApiResponse.success(new CouponAdminV1Dto.CouponTemplateListResponse(coupons, totalCount, page, size));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> getTemplate(@PathVariable Long couponId) {
        CouponInfo.CouponTemplateInfo info = couponAdminService.getTemplate(couponId);
        return ApiResponse.success(CouponAdminV1Dto.CouponTemplateResponse.from(info));
    }

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> createTemplate(
        @Valid @RequestBody CouponAdminV1Dto.CreateRequest request
    ) {
        CouponInfo.CouponTemplateInfo info = couponAdminService.create(
            request.name(), request.type(), request.value(),
            request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponTemplateResponse.from(info));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> updateTemplate(
        @PathVariable Long couponId,
        @Valid @RequestBody CouponAdminV1Dto.UpdateRequest request
    ) {
        CouponInfo.CouponTemplateInfo info = couponAdminService.update(
            couponId, request.name(), request.type(), request.value(),
            request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponTemplateResponse.from(info));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Object> deleteTemplate(@PathVariable Long couponId) {
        couponAdminService.delete(couponId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<CouponAdminV1Dto.IssuedCouponListResponse> getIssuedCoupons(
        @PathVariable Long couponId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<CouponAdminV1Dto.IssuedCouponResponse> issues = couponAdminService.getIssuedCoupons(couponId, page, size).stream()
            .map(CouponAdminV1Dto.IssuedCouponResponse::from)
            .toList();
        long totalCount = couponAdminService.getIssuedCouponCount(couponId);
        return ApiResponse.success(new CouponAdminV1Dto.IssuedCouponListResponse(issues, totalCount, page, size));
    }
}
