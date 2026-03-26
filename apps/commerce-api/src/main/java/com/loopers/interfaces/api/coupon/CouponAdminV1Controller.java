package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
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

    private final CouponFacade couponFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<CouponAdminV1Dto.CouponResponse>> getAll(Pageable pageable) {
        Page<CouponAdminV1Dto.CouponResponse> response = couponFacade.getCoupons(pageable)
            .map(CouponAdminV1Dto.CouponResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(@PathVariable Long couponId) {
        CouponInfo info = couponFacade.getCoupon(couponId);
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> register(
        @Valid @RequestBody CouponAdminV1Dto.RegisterRequest request
    ) {
        CouponInfo info = couponFacade.register(
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt(),
            request.issueLimit()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> update(
        @PathVariable Long couponId,
        @Valid @RequestBody CouponAdminV1Dto.UpdateRequest request
    ) {
        CouponInfo info = couponFacade.update(
            couponId,
            request.name(),
            request.type(),
            request.value(),
            request.minOrderAmount(),
            request.expiredAt(),
            request.issueLimit()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long couponId) {
        couponFacade.delete(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<Page<CouponAdminV1Dto.CouponIssueResponse>> getIssues(@PathVariable Long couponId, Pageable pageable) {
        Page<UserCouponInfo> issues = couponFacade.getCouponIssues(couponId, pageable);
        return ApiResponse.success(issues.map(CouponAdminV1Dto.CouponIssueResponse::from));
    }
}
