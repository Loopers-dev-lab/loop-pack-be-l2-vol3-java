package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.dto.CouponIssueRequestResDto;
import com.loopers.application.coupon.dto.FindMyCouponResDto;
import com.loopers.application.coupon.dto.IssueCouponResDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.dto.FindMyCouponApiResDto;
import com.loopers.interfaces.api.coupon.dto.IssueCouponApiResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class CouponV1Controller implements CouponV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final CouponFacade couponFacade;

    @PostMapping("/coupons/{couponId}/issue")
    @Override
    public ApiResponse<IssueCouponApiResDto> issueCoupon(@RequestHeader(HEADER_LOGIN_ID) String loginId,
                                                         @RequestHeader(HEADER_LOGIN_PW) String password,
                                                         @PathVariable Long couponId) {
        IssueCouponResDto result = couponFacade.issueCoupon(loginId, password, couponId);
        return ApiResponse.success(IssueCouponApiResDto.from(result));
    }

    @GetMapping("/users/me/coupons")
    @Override
    public ApiResponse<Page<FindMyCouponApiResDto>> getMyCoupons(@RequestHeader(HEADER_LOGIN_ID) String loginId,
                                                                 @RequestHeader(HEADER_LOGIN_PW) String password,
                                                                 Pageable pageable) {
        Page<FindMyCouponResDto> result = couponFacade.getMyCoupons(loginId, password, pageable);
        return ApiResponse.success(result.map(FindMyCouponApiResDto::from));
    }

    @PostMapping("/coupons/{couponId}/first-come-issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CouponIssueRequestResDto> firstComeIssueCoupon(
            @RequestHeader(HEADER_LOGIN_ID) String loginId,
            @RequestHeader(HEADER_LOGIN_PW) String password,
            @PathVariable Long couponId) {
        return ApiResponse.success(couponFacade.requestFirstComeIssue(loginId, password, couponId));
    }

    @GetMapping("/coupon-issues/{requestId}/status")
    public ApiResponse<CouponIssueRequestResDto> getIssueStatus(
            @RequestHeader(HEADER_LOGIN_ID) String loginId,
            @RequestHeader(HEADER_LOGIN_PW) String password,
            @PathVariable Long requestId) {
        return ApiResponse.success(couponFacade.getIssueRequestStatus(loginId, password, requestId));
    }
}
