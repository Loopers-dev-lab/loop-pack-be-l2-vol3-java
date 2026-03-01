package com.loopers.interfaces.api.coupon.v1;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.coupon.CouponResult;
import com.loopers.application.coupon.RegisterCouponUseCase;
import com.loopers.interfaces.api.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/coupons")
public class CouponV1AdminApi implements CouponV1AdminApiSpec {

    private final RegisterCouponUseCase registerCouponUseCase;

    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    @Override
    public ApiResponse<CouponDto.CreateCouponResponse> createCoupon(@RequestBody @Valid CouponDto.CreateCouponRequest request) {
        CouponResult result = registerCouponUseCase.execute(request.toCreateCouponCommand());
        return ApiResponse.success(CouponDto.CreateCouponResponse.from(result));
    }
}
