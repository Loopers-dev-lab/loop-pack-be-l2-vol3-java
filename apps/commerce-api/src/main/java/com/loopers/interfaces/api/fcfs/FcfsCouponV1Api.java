package com.loopers.interfaces.api.fcfs;

import com.loopers.application.fcfs.CouponIssueApplicationService;
import com.loopers.application.fcfs.CouponIssueRequestResult;
import com.loopers.application.fcfs.CouponIssueResultResponse;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 선착순 쿠폰 API.
 */
@RestController
@RequestMapping("/api/v1/fcfs-coupons")
@RequiredArgsConstructor
public class FcfsCouponV1Api {

    private final CouponIssueApplicationService couponIssueApplicationService;

    /**
     * 쿠폰 발급 요청.
     * POST /api/v1/fcfs-coupons/{couponId}/issue
     */
    @PostMapping("/{couponId}/issue")
    public ResponseEntity<ApiResponse<FcfsCouponV1Dto.IssueResponse>> requestIssue(
        @PathVariable Long couponId,
        @RequestBody FcfsCouponV1Dto.IssueRequest request
    ) {
        CouponIssueRequestResult result = couponIssueApplicationService.requestIssue(couponId, request.userId());

        FcfsCouponV1Dto.IssueResponse response = new FcfsCouponV1Dto.IssueResponse(
            result.requestId(),
            result.status()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(response));
    }

    /**
     * 발급 결과 조회.
     * GET /api/v1/fcfs-coupons/issue-result/{requestId}
     */
    @GetMapping("/issue-result/{requestId}")
    public ResponseEntity<ApiResponse<FcfsCouponV1Dto.IssueResultResponse>> getIssueResult(
        @PathVariable String requestId
    ) {
        CouponIssueResultResponse result = couponIssueApplicationService.getIssueResult(requestId);

        FcfsCouponV1Dto.IssueResultResponse response = new FcfsCouponV1Dto.IssueResultResponse(
            result.requestId(),
            result.status(),
            result.failureReason()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
