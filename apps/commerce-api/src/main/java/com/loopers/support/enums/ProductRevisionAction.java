package com.loopers.support.enums;

/**
 * 상품 변경 이력(ProductRevision)의 변경 유형.
 */
public enum ProductRevisionAction {
    /** 상품 최초 등록 */
    CREATE,
    /** 상품 정보 수정 */
    UPDATE,
    /** display_status를 HIDDEN으로 변경 */
    HIDE,
    /** 판매 상태 변경 (ON_SALE ↔ TEMP_SOLD_OUT ↔ STOPPED) */
    SALE_STATUS_CHANGE,
    /** 소프트 삭제 */
    DELETE,
    /** 삭제 복구 */
    RESTORE
}
