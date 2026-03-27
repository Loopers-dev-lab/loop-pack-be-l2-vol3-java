package com.loopers.domain.coupon;

import lombok.RequiredArgsConstructor;

/**
 * 쿠폰 재고 발급 시도 결과.
 */
@RequiredArgsConstructor
public enum CouponIssueStatus {

    SUCCESS(1),
    SOLD_OUT(0),
    DUPLICATE(-1),
    UNAVAILABLE(-2);

    private final int code;

    /**
     * Redis Lua Script 반환값을 결과로 변환한다.
     *
     * @param code Lua Script 반환값
     * @return 대응하는 결과
     */
    public static CouponIssueStatus fromCode(int code) {
        return switch (code) {
            case 1 -> SUCCESS;
            case 0 -> SOLD_OUT;
            case -1 -> DUPLICATE;
            case -2 -> UNAVAILABLE;
            default -> throw new IllegalArgumentException("Unknown stock issue result code: " + code);
        };
    }
}
