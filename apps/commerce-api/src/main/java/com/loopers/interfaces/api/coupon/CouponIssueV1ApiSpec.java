package com.loopers.interfaces.api.coupon;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Coupon Issue Async", description = "선착순 쿠폰 비동기 발급 API")
public interface CouponIssueV1ApiSpec {

    @Operation(summary = "선착순 쿠폰 발급 요청")
    ResponseEntity<CouponIssueV1Dto.IssueAsyncResponse> requestIssue(
            @PathVariable Long couponId,
            @RequestHeader("X-Member-Id") Long memberId);

    @Operation(summary = "발급 결과 SSE 구독")
    SseEmitter subscribeResult(
            @PathVariable String requestId,
            @RequestHeader("X-Member-Id") Long memberId);
}
