package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueApp;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.SseEmitterRegistry;
import com.loopers.domain.coupon.CouponIssueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class CouponIssueV1Controller implements CouponIssueV1ApiSpec {

    private final CouponIssueApp couponIssueApp;
    private final SseEmitterRegistry sseEmitterRegistry;

    @PostMapping("/api/v1/coupons/{couponId}/issue/async")
    @Override
    public ResponseEntity<CouponIssueV1Dto.IssueAsyncResponse> requestIssue(
            @PathVariable Long couponId,
            @RequestHeader("X-Member-Id") Long memberId) {
        CouponIssueRequestInfo info = couponIssueApp.requestIssue(couponId, memberId);
        return ResponseEntity.accepted()
                .body(new CouponIssueV1Dto.IssueAsyncResponse(info.requestId(), info.status().name()));
    }

    @GetMapping("/api/v1/coupons/issue/{requestId}/result")
    @Override
    public SseEmitter subscribeResult(
            @PathVariable String requestId,
            @RequestHeader("X-Member-Id") Long memberId) {
        CouponIssueRequestInfo info = couponIssueApp.getIssueStatus(requestId, memberId);
        if (info.status() != CouponIssueStatus.PENDING) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name("result").data(info.status().name()));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        return sseEmitterRegistry.register(requestId);
    }
}
