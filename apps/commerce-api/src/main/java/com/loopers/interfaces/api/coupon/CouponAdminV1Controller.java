package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponAdminFacade;
import com.loopers.application.coupon.dto.FindCouponTemplateResDto;
import com.loopers.application.coupon.dto.FindIssuedCouponResDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.dto.CreateCouponTemplateApiReqDto;
import com.loopers.interfaces.api.coupon.dto.FindCouponTemplateApiResDto;
import com.loopers.interfaces.api.coupon.dto.FindIssuedCouponApiResDto;
import com.loopers.interfaces.api.coupon.dto.UpdateCouponTemplateApiReqDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminV1Controller implements CouponAdminV1ApiSpec {

    private final CouponAdminFacade couponAdminFacade;

    @PostMapping
    @Override
    public ApiResponse<FindCouponTemplateApiResDto> createTemplate(@RequestBody @Valid CreateCouponTemplateApiReqDto request) {
        FindCouponTemplateResDto result = couponAdminFacade.createTemplate(request.toCommand());
        return ApiResponse.success(FindCouponTemplateApiResDto.from(result));
    }

    @GetMapping
    @Override
    public ApiResponse<Page<FindCouponTemplateApiResDto>> getTemplates(Pageable pageable) {
        Page<FindCouponTemplateResDto> result = couponAdminFacade.getTemplates(pageable);
        return ApiResponse.success(result.map(FindCouponTemplateApiResDto::from));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<FindCouponTemplateApiResDto> getTemplate(@PathVariable Long couponId) {
        FindCouponTemplateResDto result = couponAdminFacade.getTemplate(couponId);
        return ApiResponse.success(FindCouponTemplateApiResDto.from(result));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<FindCouponTemplateApiResDto> updateTemplate(@PathVariable Long couponId,
                                                                   @RequestBody @Valid UpdateCouponTemplateApiReqDto request) {
        FindCouponTemplateResDto result = couponAdminFacade.updateTemplate(couponId, request.toCommand());
        return ApiResponse.success(FindCouponTemplateApiResDto.from(result));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Void> deleteTemplate(@PathVariable Long couponId) {
        couponAdminFacade.deleteTemplate(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<Page<FindIssuedCouponApiResDto>> getIssuedCoupons(@PathVariable Long couponId, Pageable pageable) {
        Page<FindIssuedCouponResDto> result = couponAdminFacade.getIssuedCoupons(couponId, pageable);
        return ApiResponse.success(result.map(FindIssuedCouponApiResDto::from));
    }
}
