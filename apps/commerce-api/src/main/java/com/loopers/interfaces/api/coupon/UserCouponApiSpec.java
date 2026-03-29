package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.IssueAsyncResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.IssueRequestStatusResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.IssueCouponResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.MyCouponResponse;
import com.loopers.interfaces.api.coupon.UserCouponV1Dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Coupon V1 API", description = "대고객 쿠폰 API")
public interface UserCouponApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 템플릿 ID로 쿠폰을 발급받는다.")
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    ApiResponse<IssueCouponResponse> issueCoupon(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @PathVariable Long couponId
    );

    @Operation(summary = "내 쿠폰 목록 조회", description = "로그인한 사용자의 발급 쿠폰 목록을 조회한다.")
    @GetMapping("/api/v1/users/me/coupons")
    ApiResponse<PageResponse<MyCouponResponse>> getMyCoupons(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    );

    @Operation(summary = "선착순 쿠폰 비동기 발급 요청", description = "Kafka를 통해 비동기로 쿠폰 발급을 요청한다. requestId로 상태를 폴링한다.")
    @PostMapping("/api/v1/coupons/{couponId}/issue-async")
    ApiResponse<IssueAsyncResponse> issueAsync(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @PathVariable Long couponId
    );

    @Operation(summary = "발급 요청 상태 조회", description = "requestId로 발급 처리 결과를 폴링한다. (PENDING/SUCCESS/FAILED)")
    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    ApiResponse<IssueRequestStatusResponse> getIssueRequestStatus(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @PathVariable String requestId
    );
}
