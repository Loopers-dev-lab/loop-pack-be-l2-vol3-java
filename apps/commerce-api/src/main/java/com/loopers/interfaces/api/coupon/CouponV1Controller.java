package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/{couponId}/issue")
    public ApiResponse<CouponV1Dto.IssueResponse> requestIssue(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String rawPassword,
        @PathVariable Long couponId
    ) {
        CouponIssueInfo info = couponFacade.requestIssue(loginId, rawPassword, couponId);
        return ApiResponse.success(CouponV1Dto.IssueResponse.from(info));
    }

    @GetMapping("/issue/{requestId}")
    public ApiResponse<CouponV1Dto.IssueResponse> getIssueResult(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String rawPassword,
        @PathVariable Long requestId
    ) {
        CouponIssueInfo info = couponFacade.getIssueResult(loginId, rawPassword, requestId);
        return ApiResponse.success(CouponV1Dto.IssueResponse.from(info));
    }
}
