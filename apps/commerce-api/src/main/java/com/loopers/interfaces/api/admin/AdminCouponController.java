package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.AdminCouponV1Dto.AdminCouponResponse;
import com.loopers.interfaces.api.admin.AdminCouponV1Dto.AdminUserCouponResponse;
import com.loopers.interfaces.api.admin.AdminCouponV1Dto.CreateCouponRequest;
import com.loopers.interfaces.api.admin.AdminCouponV1Dto.PageResponse;
import com.loopers.interfaces.api.admin.AdminCouponV1Dto.UpdateCouponRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1")
public class AdminCouponController implements AdminCouponApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping("/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<AdminCouponResponse> createCoupon(@RequestBody CreateCouponRequest request) {
        return ApiResponse.success(AdminCouponResponse.from(
            couponFacade.createCoupon(
                request.name(),
                request.type(),
                request.value() != null ? request.value() : 0,
                request.minOrderAmount() != null ? request.minOrderAmount() : 0,
                request.expiredAt(),
                request.maxIssuable()
            )
        ));
    }

    @PutMapping("/coupons/{couponId}")
    @Override
    public ApiResponse<AdminCouponResponse> updateCoupon(@PathVariable Long couponId, @RequestBody UpdateCouponRequest request) {
        return ApiResponse.success(AdminCouponResponse.from(
            couponFacade.updateCoupon(
                couponId,
                request.name(),
                request.type(),
                request.value(),
                request.minOrderAmount() != null ? request.minOrderAmount() : 0,
                request.expiredAt()
            )
        ));
    }

    @DeleteMapping("/coupons/{couponId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public void deleteCoupon(@PathVariable Long couponId) {
        couponFacade.deleteCoupon(couponId);
    }

    @GetMapping("/coupons/{couponId}")
    @Override
    public ApiResponse<AdminCouponResponse> getCoupon(@PathVariable Long couponId) {
        return ApiResponse.success(AdminCouponResponse.from(couponFacade.getCoupon(couponId)));
    }

    @GetMapping("/coupons")
    @Override
    public ApiResponse<PageResponse<AdminCouponResponse>> getCoupons(
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        return ApiResponse.success(PageResponse.from(
            couponFacade.getCoupons(page, size).map(AdminCouponResponse::from)
        ));
    }

    @GetMapping("/coupons/{couponId}/issues")
    @Override
    public ApiResponse<PageResponse<AdminUserCouponResponse>> getIssuedCoupons(
        @PathVariable Long couponId,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        return ApiResponse.success(PageResponse.from(
            couponFacade.getIssuedCoupons(couponId, page, size).map(AdminUserCouponResponse::from)
        ));
    }
}
