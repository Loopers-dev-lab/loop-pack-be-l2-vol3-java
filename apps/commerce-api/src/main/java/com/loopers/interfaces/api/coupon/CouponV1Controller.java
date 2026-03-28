package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueService;
import com.loopers.application.coupon.CouponService;
import com.loopers.domain.coupon.CouponIssueModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller {

    private final CouponService couponService;
    private final CouponIssueService couponIssueService;
    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.CouponIssueResponse> issueCoupon(
        @LoginMember MemberModel member,
        @PathVariable Long couponId
    ) {
        CouponModel coupon = couponService.getCoupon(couponId);
        CouponIssueModel issue = couponIssueService.issue(coupon, member.getId());
        return ApiResponse.success(CouponV1Dto.CouponIssueResponse.from(issue));
    }

    /**
     * 선착순 쿠폰 발급 요청 (비동기 — Kafka로 위임)
     */
    @PostMapping("/api/v1/coupons/{couponId}/issue-async")
    public ApiResponse<Void> requestCouponIssue(
        @LoginMember MemberModel member,
        @PathVariable Long couponId
    ) {
        couponFacade.requestCouponIssue(couponId, member.getId());
        return ApiResponse.success(null);
    }

    /**
     * 쿠폰 발급 결과 확인 (Polling)
     * 비동기 발급 요청 후 클라이언트가 주기적으로 호출하여 발급 결과를 확인한다.
     */
    @GetMapping("/api/v1/coupons/{couponId}/issue-status")
    public ApiResponse<CouponV1Dto.CouponIssueStatusResponse> getCouponIssueStatus(
        @LoginMember MemberModel member,
        @PathVariable Long couponId
    ) {
        Optional<CouponIssueModel> issue = couponIssueService.findByUserAndCoupon(member.getId(), couponId);
        CouponV1Dto.CouponIssueStatusResponse response = issue
            .map(i -> new CouponV1Dto.CouponIssueStatusResponse("ISSUED", CouponV1Dto.CouponIssueResponse.from(i)))
            .orElseGet(() -> new CouponV1Dto.CouponIssueStatusResponse("PENDING", null));
        return ApiResponse.success(response);
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
