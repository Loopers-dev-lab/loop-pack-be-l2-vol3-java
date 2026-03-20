package com.loopers.support.enums;

/**
 * 주문 상태 머신. PENDING_PAYMENT → CANCELLED 또는 EXPIRED로만 전이 가능.
 */
public enum OrderStatus {
    /** 결제 대기 중 — 취소/만료 가능 */
    PENDING_PAYMENT,
    /** 결제 완료된 주문 */
    PAID,
    /** 사용자가 취소한 주문 */
    CANCELLED,
    /** 15분 경과로 자동 만료된 주문 */
    EXPIRED;

    /**
     * 현재 상태에서 사용자 취소가 가능한지 판별한다. PENDING_PAYMENT만 취소 가능.
     *
     * @return 취소 가능하면 true
     */
    public boolean canCancel() {
        return this == PENDING_PAYMENT;
    }
}
