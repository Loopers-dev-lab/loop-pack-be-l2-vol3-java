package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.MyCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.CurrentUser;
import com.loopers.interfaces.auth.LoginRequired;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class CouponController {

    private final CouponFacade couponFacade;

    @ResponseStatus(HttpStatus.CREATED)
    @LoginRequired
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ApiResponse<CouponDto.IssueResponse> issue(@PathVariable Long couponId,
                                                      @CurrentUser AuthenticatedUser user) {

        Long issuedCouponId = couponFacade.issue(couponId, user.id());
        return ApiResponse.success(new CouponDto.IssueResponse(issuedCouponId));
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @LoginRequired
    @PostMapping("/api/v1/coupons/{couponId}/issue-async")
    public ApiResponse<CouponDto.IssueAsyncResponse> issueAsync(@PathVariable Long couponId,
                                                                 @CurrentUser AuthenticatedUser user) {
        String requestId = couponFacade.requestIssue(couponId, user.id());
        return ApiResponse.success(new CouponDto.IssueAsyncResponse(requestId));
    }

    @LoginRequired
    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    public ApiResponse<CouponDto.IssueRequestStatusResponse> getIssueRequestStatus(@PathVariable String requestId) {
        CouponIssueRequestInfo info = couponFacade.getIssueRequestStatus(requestId);
        return ApiResponse.success(CouponDto.IssueRequestStatusResponse.from(info));
    }

    @LoginRequired
    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<CouponDto.MyCouponList> getList(@CurrentUser AuthenticatedUser user) {
        List<MyCouponInfo> myCouponInfos = couponFacade.getMyCouponList(user.id());
        CouponDto.MyCouponList response = CouponDto.MyCouponList.from(myCouponInfos);
        return ApiResponse.success(response);
    }
}
