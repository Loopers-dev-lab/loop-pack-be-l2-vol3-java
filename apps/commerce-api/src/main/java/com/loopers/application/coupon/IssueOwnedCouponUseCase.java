package com.loopers.application.coupon;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.CouponIssueEventPublisher;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponStockManager;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 사용자에게 쿠폰을 발급합니다.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class IssueOwnedCouponUseCase {

    private final CouponStockManager couponStockManager;
    private final CouponIssueEventPublisher couponIssueEventPublisher;

    /**
     * 쿠폰 발급을 실행한다.
     *
     * <ol>
     *   <li>Redis에서 수량/중복을 선검증한다 (DB 접근 없음).</li>
     *   <li>성공 시 Kafka에 발급 이벤트를 발행한다.</li>
     * </ol>
     *
     * @param couponId 발급할 쿠폰 ID
     * @param userId   발급 대상 사용자 ID
     * @throws CoreException 수량이 소진되었거나, 이미 발급되었거나, 쿠폰이 초기화되지 않은 경우
     */
    public void execute(Long couponId, Long userId) throws CoreException {
        CouponIssueStatus result = couponStockManager.issueCoupon(couponId, userId);

        switch (result) {
            case UNAVAILABLE -> throw new CoreException(ErrorType.SERVICE_UNAVAILABLE);
            case DUPLICATE -> throw new CoreException(ErrorType.ALREADY_COUPON_ISSUED);
            case SOLD_OUT -> throw new CoreException(ErrorType.COUPON_SOLD_OUT);
            case SUCCESS -> couponIssueEventPublisher.publishEvent(couponId, userId);
        }
    }
}
