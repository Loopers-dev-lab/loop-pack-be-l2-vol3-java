package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponUserService;
import com.loopers.application.coupon.IssuedCouponResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 쿠폰 사용자 API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller {

    private final CouponUserService couponUserService;

    /**
     * 쿠폰 발급.
     */
    @PostMapping("/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.IssuedCouponResponseDto> issueCoupon(
            @PathVariable Long couponId,
            @UserId Long userId) {
        IssuedCouponResult result = couponUserService.issue(userId, couponId);
        return ApiResponse.success(CouponV1Dto.IssuedCouponResponseDto.from(result));
    }

    /**
     * 내 쿠폰 목록 조회.
     */
    @GetMapping("/users/me/coupons")
    public ApiResponse<CouponV1Dto.MyCouponListResponseDto> getMyCoupons(
            @UserId Long userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        List<IssuedCouponResult> coupons = couponUserService.findMyCoupons(userId, offset, limit);
        long totalCount = couponUserService.countMyCoupons(userId);

        List<CouponV1Dto.IssuedCouponResponseDto> dtos = coupons.stream()
            .map(CouponV1Dto.IssuedCouponResponseDto::from)
            .toList();

        return ApiResponse.success(new CouponV1Dto.MyCouponListResponseDto(dtos, totalCount));
    }

    /**
     * 내 쿠폰 상세 조회.
     */
    @GetMapping("/users/me/coupons/{couponId}")
    public ApiResponse<CouponV1Dto.IssuedCouponResponseDto> getMyCoupon(
            @UserId Long userId,
            @PathVariable Long couponId) {
        IssuedCouponResult result = couponUserService.findMyCouponById(userId, couponId);
        return ApiResponse.success(CouponV1Dto.IssuedCouponResponseDto.from(result));
    }
}
