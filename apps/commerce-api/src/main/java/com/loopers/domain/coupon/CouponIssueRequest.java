package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponIssueRequest(
        UUID requestId,
        String memberId,
        UUID couponId,
        CouponIssueRequestStatus status,
        String failureReason,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
    public CouponIssueRequest {
        if (requestId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 요청 식별자는 필수입니다.");
        }
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 식별자는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 식별자는 필수입니다.");
        }
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 요청 상태는 필수입니다.");
        }
        if (requestedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 요청 시각은 필수입니다.");
        }
    }
}
