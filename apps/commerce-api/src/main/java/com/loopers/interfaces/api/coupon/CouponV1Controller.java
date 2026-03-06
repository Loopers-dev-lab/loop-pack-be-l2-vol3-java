package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller {

    private final CouponFacade couponFacade;
    private final CouponService couponService;

    @PostMapping("/coupons/{couponTemplateId}/issue")
    public ApiResponse<CouponV1Dto.CouponResponse> issueCoupon(
        @RequestHeader("X-Loopers-UserId") Long userId,
        @PathVariable Long couponTemplateId
    ) {
        CouponInfo info = couponFacade.issueCoupon(userId, couponTemplateId);
        return ApiResponse.success(CouponV1Dto.CouponResponse.from(info));
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.CouponResponse>> getMyCoupons(
        @RequestHeader("X-Loopers-UserId") Long userId
    ) {
        List<CouponV1Dto.CouponResponse> coupons = couponFacade.getUserCoupons(userId).stream()
            .map(CouponV1Dto.CouponResponse::from)
            .toList();
        return ApiResponse.success(coupons);
    }

    @PostMapping("/admin/coupon-templates")
    public ApiResponse<Object> createTemplate(
        @RequestBody @Valid CouponV1Dto.CreateTemplateRequest request
    ) {
        CouponTemplate template = new CouponTemplate(
            request.name(),
            DiscountType.valueOf(request.discountType()),
            request.discountValue(),
            request.minOrderAmount(),
            request.maxDiscountAmount(),
            request.validDays()
        );
        couponService.createTemplate(template);
        return ApiResponse.success();
    }
}
