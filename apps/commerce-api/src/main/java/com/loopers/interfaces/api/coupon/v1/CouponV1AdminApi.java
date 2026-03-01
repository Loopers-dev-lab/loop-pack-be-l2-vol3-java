package com.loopers.interfaces.api.coupon.v1;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.coupon.CouponResult;
import com.loopers.application.coupon.ReadCouponDetailUseCase;
import com.loopers.application.coupon.ReadCouponsUseCase;
import com.loopers.application.coupon.RegisterCouponUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/coupons")
public class CouponV1AdminApi implements CouponV1AdminApiSpec {

    private final RegisterCouponUseCase registerCouponUseCase;
    private final ReadCouponsUseCase readCouponsUseCase;
    private final ReadCouponDetailUseCase readCouponDetailUseCase;

    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    @Override
    public ApiResponse<CouponDto.CreateCouponResponse> createCoupon(@RequestBody @Valid CouponDto.CreateCouponRequest request) {
        CouponResult result = registerCouponUseCase.execute(request.toCreateCouponCommand());
        return ApiResponse.success(CouponDto.CreateCouponResponse.from(result));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<CouponDto.CouponResponse>> getCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<CouponResult> result = readCouponsUseCase.execute(PageSize.withMaxSize(page, size));
        return ApiResponse.success(new PageResponse<>(CouponDto.CouponResponse.from(result.content()), result.hasNext()));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<CouponDto.CouponResponse> getCoupon(@PathVariable Long couponId) {
        CouponResult result = readCouponDetailUseCase.execute(couponId);
        return ApiResponse.success(CouponDto.CouponResponse.from(result));
    }
}
