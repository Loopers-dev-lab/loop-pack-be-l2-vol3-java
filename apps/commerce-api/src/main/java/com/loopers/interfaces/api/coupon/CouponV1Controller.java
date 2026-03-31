package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueFacade;
import com.loopers.application.coupon.CouponIssueRequestFacade;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponV1Controller {

    private final CouponIssueFacade couponIssueFacade;
    private final CouponIssueRequestFacade couponIssueRequestFacade;

    @PostMapping("/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(
        @LoginUser Long userId,
        @PathVariable Long couponId
    ) {
        IssuedCouponInfo info = couponIssueFacade.issue(userId, couponId);
        return ApiResponse.success(CouponV1Dto.IssuedCouponResponse.from(info));
    }

    @PostMapping("/{couponId}/issue-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CouponV1Dto.IssueRequestResponse> issueRequest(
        @LoginUser Long userId,
        @PathVariable Long couponId
    ) {
        CouponIssueRequestInfo info = couponIssueRequestFacade.issueRequest(userId, couponId);
        return ApiResponse.success(CouponV1Dto.IssueRequestResponse.from(info));
    }

    @GetMapping("/issue-requests/{requestId}")
    public ApiResponse<CouponV1Dto.IssueRequestResponse> getIssueRequest(
        @LoginUser Long userId,
        @PathVariable Long requestId
    ) {
        CouponIssueRequestInfo info = couponIssueRequestFacade.getIssueRequest(userId, requestId);
        return ApiResponse.success(CouponV1Dto.IssueRequestResponse.from(info));
    }
}
