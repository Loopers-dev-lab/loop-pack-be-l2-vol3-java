package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class CouponAdminV1Controller implements CouponAdminApiV1Spec {

    private final CouponFacade couponFacade;

    // Command

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> register(
            @RequestBody @Valid CouponRequest.Register request) {
        CouponInfo info = couponFacade.registerCoupon(request.toCommand());
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long couponId) {
        couponFacade.deleteCoupon(couponId);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> update(
            @PathVariable Long couponId,
            @RequestBody @Valid CouponRequest.Update request) {
        CouponInfo info = couponFacade.updateCoupon(couponId, request.toCommand());
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    // Query

    @GetMapping
    @Override
    public ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>> list(
            @Valid CouponRequest.ListAll request) {
        Page<CouponInfo> coupons = couponFacade.getCoupons(request.toPageable());
        return ApiResponse.success(PageResponse.from(coupons, CouponAdminV1Dto.CouponResponse::from));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> detail(@PathVariable Long couponId) {
        CouponInfo info = couponFacade.getCoupon(couponId);
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }
}
