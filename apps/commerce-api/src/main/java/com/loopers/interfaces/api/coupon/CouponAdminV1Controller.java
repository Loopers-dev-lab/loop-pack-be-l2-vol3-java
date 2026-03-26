package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.domain.coupon.CreateCouponCommand;
import com.loopers.domain.coupon.UpdateCouponCommand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class CouponAdminV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping
    public ApiResponse<CouponV1Dto.Response> createCoupon(
            @RequestBody CouponV1Dto.CreateRequest request
    ) {
        CreateCouponCommand command = new CreateCouponCommand(
                request.name(), request.type(), request.value(),
                request.minOrderAmount(), request.expiredAt(), request.totalQuantity()
        );
        CouponInfo couponInfo = couponFacade.createCoupon(command);
        return ApiResponse.success(CouponV1Dto.Response.from(couponInfo));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.Response> getCoupon(@PathVariable Long couponId) {
        CouponInfo couponInfo = couponFacade.getCoupon(couponId);
        return ApiResponse.success(CouponV1Dto.Response.from(couponInfo));
    }

    @GetMapping
    public ApiResponse<CouponV1Dto.PageResponse> getCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<CouponInfo> coupons = couponFacade.getCoupons(pageable);
        return ApiResponse.success(CouponV1Dto.PageResponse.from(coupons));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponV1Dto.Response> updateCoupon(
            @PathVariable Long couponId,
            @RequestBody CouponV1Dto.UpdateRequest request
    ) {
        UpdateCouponCommand command = new UpdateCouponCommand(
                request.name(), request.type(), request.value(),
                request.minOrderAmount(), request.expiredAt()
        );
        CouponInfo couponInfo = couponFacade.updateCoupon(couponId, command);
        return ApiResponse.success(CouponV1Dto.Response.from(couponInfo));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(@PathVariable Long couponId) {
        couponFacade.deleteCoupon(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<CouponV1Dto.UserCouponPageResponse> getIssuedCoupons(
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserCouponInfo> issuedCoupons = couponFacade.getIssuedCoupons(couponId, pageable);
        return ApiResponse.success(CouponV1Dto.UserCouponPageResponse.from(issuedCoupons));
    }
}
