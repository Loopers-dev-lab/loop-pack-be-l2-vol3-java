package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueRequestInfo;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
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
        ZonedDateTime now = couponFacade.now();
        return ApiResponse.success(CouponDto.CouponIssueResponse.from(couponIssue, now));
    }

    @PostMapping("/api/v1/coupons/{couponId}/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CouponDto.CouponIssueRequestResponse> requestCouponIssue(
        @AuthMember Member member,
        @PathVariable Long couponId
    ) {
        CouponIssueRequestInfo requestInfo = couponFacade.requestCouponIssue(couponId, member.getId());
        return ApiResponse.success(CouponDto.CouponIssueRequestResponse.from(requestInfo));
    }

    @GetMapping("/api/v1/coupons/requests/{requestId}")
    public ApiResponse<CouponDto.CouponIssueRequestResponse> getIssueRequest(
        @PathVariable Long requestId
    ) {
        CouponIssueRequestInfo requestInfo = couponFacade.getIssueRequest(requestId);
        return ApiResponse.success(CouponDto.CouponIssueRequestResponse.from(requestInfo));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<List<CouponDto.CouponIssueResponse>> getMyCoupons(
        @AuthMember Member member
    ) {
        ZonedDateTime now = couponFacade.now();
        List<CouponDto.CouponIssueResponse> responses = couponFacade.getMyCoupons(member.getId())
            .stream()
            .map(issue -> CouponDto.CouponIssueResponse.from(issue, now))
            .toList();
        return ApiResponse.success(responses);
    }
}
