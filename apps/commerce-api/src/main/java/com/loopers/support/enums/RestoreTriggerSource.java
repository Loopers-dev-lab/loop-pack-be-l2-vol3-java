package com.loopers.support.enums;

/**
 * 장바구니 복원 트리거 출처. OrderCartRestoreModel에서 사용.
 */
public enum RestoreTriggerSource {
    /** 고객 주문 취소 API에서 트리거 */
    CANCEL_API,
    /** PG사 웹훅에서 트리거 (Phase2) */
    PG_WEBHOOK,
    /** 만료 배치 스케줄러에서 트리거 */
    EXPIRE_JOB,
    /** 운영자 수동 처리 */
    MANUAL
}
