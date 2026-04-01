package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CouponController implements CouponApiSpec {

    private final CouponFacade couponFacade;

    public CouponController(CouponFacade couponFacade) {
        this.couponFacade = couponFacade;
    }

    /**
     * 선착순 쿠폰 발급 요청 (비동기 FCFS)
     *
     * 모든 쿠폰 발급은 Kafka 파이프라인을 통해 처리된다.
     * 202 Accepted 응답 후 클라이언트가 결과를 polling.
     */
    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Override
    public ApiResponse<CouponResponse.CouponIssueRequestResponse> requestCouponIssue(
            @AuthUser User user,
            @PathVariable Long couponId) {
        CouponFacade.CouponIssueRequestResult result =
                couponFacade.requestCouponIssue(couponId, user.getId());
        return ApiResponse.success(new CouponResponse.CouponIssueRequestResponse(
                result.requestId(), result.eventId(), result.status()));
    }

    @GetMapping("/api/v1/coupons/issue-requests/{requestId}")
    @Override
    public ApiResponse<CouponResponse.CouponIssueRequestResponse> getCouponIssueResult(
            @AuthUser User user,
            @PathVariable Long requestId) {
        CouponFacade.CouponIssueRequestResult result =
                couponFacade.getCouponIssueResult(requestId);
        return ApiResponse.success(new CouponResponse.CouponIssueRequestResponse(
                result.requestId(), result.eventId(), result.status()));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponResponse.CouponListResponse> getMyCoupons(@AuthUser User user) {
        CouponFacade.CouponListResult result = couponFacade.getMyCoupons(user.getId());

        List<CouponResponse.IssuedCouponDetail> details = result.coupons().stream()
                .map(c -> new CouponResponse.IssuedCouponDetail(
                        c.issuedCouponId(), c.couponTemplateId(),
                        c.couponName(), c.discountType(),
                        c.discountValue(), c.maxDiscountAmount(),
                        c.status(), c.usedAt(), c.createdAt()))
                .toList();

        return ApiResponse.success(new CouponResponse.CouponListResponse(details));
    }

    @GetMapping("/api/v1/coupons")
    @Override
    public ApiResponse<CouponResponse.AvailableCouponListResponse> getAvailableCoupons(@AuthUser User user) {
        CouponFacade.AvailableCouponListResult result = couponFacade.getAvailableCoupons();

        List<CouponResponse.AvailableCouponDetail> details = result.coupons().stream()
                .map(c -> new CouponResponse.AvailableCouponDetail(
                        c.couponTemplateId(), c.name(), c.description(),
                        c.discountType(), c.discountValue(),
                        c.maxDiscountAmount(), c.minOrderAmount(),
                        c.validFrom(), c.validTo()))
                .toList();

        return ApiResponse.success(new CouponResponse.AvailableCouponListResponse(details));
    }
}
