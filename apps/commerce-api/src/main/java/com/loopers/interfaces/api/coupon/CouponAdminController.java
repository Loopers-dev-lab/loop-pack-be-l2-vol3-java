package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponDetailInfo;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AdminOnly;
import com.loopers.support.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
public class CouponAdminController {

    private final CouponFacade couponFacade;

    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api-admin/v1/coupons")
    public ApiResponse<CouponAdminDto.RegisterResponse> register(@RequestBody CouponAdminDto.RegisterRequest request) {
        Long couponId = couponFacade.register(request.name(), request.type(), request.value(), request.totalQuantity(), request.expiredAt());
        return ApiResponse.success(new CouponAdminDto.RegisterResponse(couponId));
    }

    @AdminOnly
    @PutMapping("/api-admin/v1/coupons/{couponId}")
    public ApiResponse<Void> update(@PathVariable Long couponId,
                                    @RequestBody CouponAdminDto.UpdateRequest request) {
        couponFacade.update(couponId, request.name(), request.value(), request.expiredAt());
        return ApiResponse.success(null);
    }

    @AdminOnly
    @DeleteMapping("/api-admin/v1/coupons/{couponId}")
    public ApiResponse<Void> delete(@PathVariable Long couponId) {
        couponFacade.delete(couponId);
        return ApiResponse.success(null);
    }

    @AdminOnly
    @GetMapping("/api-admin/v1/coupons")
    public ApiResponse<PageResponse<CouponAdminDto.ListResponse>> getList(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        PageResponse<CouponAdminDto.ListResponse> response = couponFacade.getList(PageRequest.of(page, size))
                .map(CouponAdminDto.ListResponse::from);
        return ApiResponse.success(response);
    }

    @AdminOnly
    @GetMapping("/api-admin/v1/coupons/{couponId}")
    public ApiResponse<CouponAdminDto.DetailResponse> getDetail(@PathVariable Long couponId) {
        CouponDetailInfo info = couponFacade.getDetail(couponId);
        return ApiResponse.success(CouponAdminDto.DetailResponse.from(info));
    }
}
