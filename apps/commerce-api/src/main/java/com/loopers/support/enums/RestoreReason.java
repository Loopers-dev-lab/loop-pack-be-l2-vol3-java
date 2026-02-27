package com.loopers.support.enums;

/**
 * 장바구니 복원 사유. OrderCartRestoreModel에서 사용.
 */
public enum RestoreReason {
    /** 사용자가 직접 주문 취소 */
    USER_CANCELLED,
    /** 15분 경과로 배치에서 만료 처리 */
    EXPIRED,
    /** 결제 실패 (Phase2) */
    PAYMENT_FAILED,
    /** PG사 측 취소 (Phase2) */
    PG_CANCELLED
}
