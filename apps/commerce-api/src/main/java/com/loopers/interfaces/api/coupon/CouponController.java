package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponDto.CouponIssueResponse> issueCoupon(
        @AuthMember Member member,
        @PathVariable Long couponId
    ) {
        CouponIssue couponIssue = couponFacade.issueCoupon(couponId, member.getId());
        return ApiResponse.success(CouponDto.CouponIssueResponse.from(couponIssue));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<List<CouponDto.CouponIssueResponse>> getMyCoupons(
        @AuthMember Member member
    ) {
        List<CouponDto.CouponIssueResponse> responses = couponFacade.getMyCoupons(member.getId())
            .stream()
            .map(CouponDto.CouponIssueResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
