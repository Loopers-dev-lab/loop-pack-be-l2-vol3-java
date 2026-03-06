package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.coupon.view.MyCouponView;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CouponController {

    private final CouponApplicationService couponApplicationService;

    @PostMapping("/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> issue(@PathVariable UUID couponId, @AuthMember Member member) {
        couponApplicationService.issue(couponId, member.id().value());
        return ApiResponse.success();
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<CouponDto.MyCouponListResponse> listMyCoupons(@AuthMember Member member) {
        List<MyCouponView> views = couponApplicationService.listMyCoupons(member.id().value());
        return ApiResponse.success(CouponDto.MyCouponListResponse.from(views));
    }
}
