package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.coupon.Coupon;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/coupons")
public class CouponAdminController {

    private final CouponFacade couponFacade;

    @GetMapping
    public ApiResponse<List<CouponDto.CouponResponse>> getCoupons() {
        List<CouponDto.CouponResponse> responses = couponFacade.getCoupons().stream()
            .map(CouponDto.CouponResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponDto.CouponResponse> getCoupon(@PathVariable Long couponId) {
        Coupon coupon = couponFacade.getCoupon(couponId);
        return ApiResponse.success(CouponDto.CouponResponse.from(coupon));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponDto.CouponResponse> createCoupon(
        @Valid @RequestBody CouponDto.CreateRequest request
    ) {
        Coupon coupon = couponFacade.createCoupon(
            request.name(), request.type(), request.value(),
            request.minOrderAmount(), request.expiredAt());
        return ApiResponse.success(CouponDto.CouponResponse.from(coupon));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponDto.CouponResponse> updateCoupon(
        @PathVariable Long couponId,
        @Valid @RequestBody CouponDto.UpdateRequest request
    ) {
        Coupon coupon = couponFacade.updateCoupon(
            couponId, request.name(), request.type(), request.value(),
            request.minOrderAmount(), request.expiredAt());
        return ApiResponse.success(CouponDto.CouponResponse.from(coupon));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Object> deleteCoupon(@PathVariable Long couponId) {
        couponFacade.deleteCoupon(couponId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<List<CouponDto.CouponIssueResponse>> getCouponIssues(
        @PathVariable Long couponId
    ) {
        List<CouponDto.CouponIssueResponse> responses = couponFacade.getCouponIssues(couponId)
            .stream()
            .map(CouponDto.CouponIssueResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
