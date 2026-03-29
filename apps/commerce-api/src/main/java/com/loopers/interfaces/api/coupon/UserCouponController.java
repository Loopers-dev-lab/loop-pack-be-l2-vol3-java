package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.IssueAsyncResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.IssueRequestStatusResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.IssueCouponResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.MyCouponResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class UserCouponController implements UserCouponApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<IssueCouponResponse> issueCoupon(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @PathVariable Long couponId
    ) {
        return ApiResponse.success(IssueCouponResponse.from(
            couponFacade.issueCoupon(loginId, password, couponId)
        ));
    }

    @PostMapping("/api/v1/coupons/{couponId}/issue-async")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public ApiResponse<IssueAsyncResponse> issueAsync(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @PathVariable Long couponId
    ) {
        return ApiResponse.success(IssueAsyncResponse.from(
            couponFacade.requestIssueAsync(loginId, password, couponId)
        ));
    }

    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    @Override
    public ApiResponse<IssueRequestStatusResponse> getIssueRequestStatus(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @PathVariable String requestId
    ) {
        return ApiResponse.success(IssueRequestStatusResponse.from(
            couponFacade.getIssueRequestStatus(loginId, password, requestId)
        ));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<PageResponse<MyCouponResponse>> getMyCoupons(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        return ApiResponse.success(PageResponse.from(
            couponFacade.getMyCoupons(loginId, password, page, size)
                .map(MyCouponResponse::from)
        ));
    }
}
