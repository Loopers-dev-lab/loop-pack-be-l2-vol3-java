package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueService;
import com.loopers.application.coupon.CouponService;
import com.loopers.domain.coupon.CouponIssueModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller {

    private final CouponService couponService;
    private final CouponIssueService couponIssueService;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.CouponIssueResponse> issueCoupon(
        @LoginMember MemberModel member,
        @PathVariable Long couponId
    ) {
        CouponModel coupon = couponService.getCoupon(couponId);
        CouponIssueModel issue = couponIssueService.issue(coupon, member.getId());
        return ApiResponse.success(CouponV1Dto.CouponIssueResponse.from(issue));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.CouponIssueResponse>> getMyCoupons(
        @LoginMember MemberModel member
    ) {
        List<CouponIssueModel> issues = couponIssueService.getMyIssues(member.getId());
        List<CouponV1Dto.CouponIssueResponse> response = issues.stream()
            .map(CouponV1Dto.CouponIssueResponse::from)
            .toList();
        return ApiResponse.success(response);
    }
}
