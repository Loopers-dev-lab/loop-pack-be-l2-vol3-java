package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponAdminFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponAdminFacade couponAdminFacade;

    // 쿠폰 템플릿 목록 조회 (US-C03)
    @GetMapping
    public ApiResponse<CouponAdminV1Dto.CouponTemplateListResponse> getTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size)
    {
        return ApiResponse.success(
                CouponAdminV1Dto.CouponTemplateListResponse.from(
                        couponAdminFacade.findAllTemplates(PageRequest.of(page, size))));
    }

    // 쿠폰 템플릿 상세 조회 (US-C04)
    @GetMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> getTemplate(
            @PathVariable Long couponId)
    {
        return ApiResponse.success(
                CouponAdminV1Dto.CouponTemplateResponse.from(
                        couponAdminFacade.findTemplateById(couponId)));
    }

    // 쿠폰 템플릿 등록 (US-C05)
    @PostMapping
    public ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> registerTemplate(
            @RequestBody CouponAdminV1Dto.CouponTemplateRegisterRequest request)
    {
        return ApiResponse.success(
                CouponAdminV1Dto.CouponTemplateResponse.from(
                        couponAdminFacade.register(request.toCommand())));
    }

    // 쿠폰 템플릿 수정 (US-C06)
    @PutMapping("/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> updateTemplate(
            @PathVariable Long couponId,
            @RequestBody CouponAdminV1Dto.CouponTemplateUpdateRequest request)
    {
        return ApiResponse.success(
                CouponAdminV1Dto.CouponTemplateResponse.from(
                        couponAdminFacade.update(couponId, request.toCommand())));
    }

    // 쿠폰 템플릿 삭제 (US-C07)
    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long couponId) {
        couponAdminFacade.delete(couponId);
        return ApiResponse.success(null);
    }

    // 특정 쿠폰 템플릿의 발급 내역 조회 (US-C08)
    @GetMapping("/{couponId}/issues")
    public ApiResponse<CouponAdminV1Dto.UserCouponListResponse> getIssuesByTemplate(
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size)
    {
        return ApiResponse.success(
                CouponAdminV1Dto.UserCouponListResponse.from(
                        couponAdminFacade.findIssuesByTemplateId(couponId, PageRequest.of(page, size))));
    }
}
