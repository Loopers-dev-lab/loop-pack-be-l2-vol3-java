package com.loopers.support.enums;

/**
 * 브랜드/상품의 고객 노출 상태.
 * 고객 API 조회 조건: del_yn='N' AND display_status='ACTIVE'
 */
public enum DisplayStatus {
    /** 고객에게 노출되는 정상 상태 */
    ACTIVE,
    /** 관리자만 볼 수 있는 비노출 상태 */
    HIDDEN
}
