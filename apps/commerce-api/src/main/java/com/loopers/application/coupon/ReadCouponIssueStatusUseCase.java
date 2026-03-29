package com.loopers.application.coupon;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.CouponIssueStatusManager;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 발급 상태를 조회합니다.
 *
 * <p>Redis에 저장된 발급 상태(PENDING/COMPLETED/FAILED)를 반환한다.
 * 상태가 존재하지 않으면 예외를 발생시킨다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadCouponIssueStatusUseCase {

    private final CouponIssueStatusManager couponIssueStatusManager;

    public Result execute(Long couponId, Long userId) {
        String status = couponIssueStatusManager.getStatus(couponId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.COUPON_ISSUE_STATUS_NOT_FOUND));

        return new Result(status);
    }

    public record Result(String status) {
    }
}
