package com.loopers.support.enums;

/**
 * 사용자 발급 쿠폰 상태.
 *
 * <pre>
 * AVAILABLE ──(주문 생성 TX, CAS)──▶ RESERVED ──(Relay 확정)──▶ USED
 *     ▲                                  │
 *     └──────(TX 롤백 시 자동 복귀)──────┘
 *     ▲                                                          │
 *     └──────────────(주문 취소/만료 시 복원)─────────────────────┘
 * </pre>
 */
public enum UserCouponStatus {
    AVAILABLE,  // 사용 가능
    RESERVED,   // 주문 생성 TX에서 선점됨 (CAS). 다른 주문에서 사용 불가
    USED,       // 사용 완료
    EXPIRED     // 쿠폰 템플릿 만료
}
