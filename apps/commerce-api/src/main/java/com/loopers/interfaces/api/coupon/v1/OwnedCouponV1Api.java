package com.loopers.interfaces.api.coupon.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.coupon.ReadMyOwnedCouponsUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class OwnedCouponV1Api implements OwnedCouponV1ApiSpec {

    private final ReadMyOwnedCouponsUseCase readMyOwnedCouponsUseCase;

    @GetMapping("/api/v1/owned-coupons")
    @Override
    public ApiResponse<PageResponse<OwnedCouponDto.MyOwnedCouponsResponse>> getMyOwnedCoupons(
            @LoginUser Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ReadMyOwnedCouponsUseCase.Result> result = readMyOwnedCouponsUseCase.execute(userId, PageSize.withMaxSize(page, size));
        return ApiResponse.success(new PageResponse<>(OwnedCouponDto.MyOwnedCouponsResponse.from(result.content()), result.hasNext()));
    }
}
