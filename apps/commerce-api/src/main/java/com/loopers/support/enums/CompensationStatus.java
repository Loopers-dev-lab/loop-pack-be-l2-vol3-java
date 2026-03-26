package com.loopers.support.enums;

/**
 * 결제 보정 상태.
 * <p>
 * stock commit 실패 등으로 결제 성공 후 후속 처리가 실패한 경우,
 * 보정 테이블에 기록하여 재시도 또는 수동 처리를 추적한다.
 * </p>
 */
public enum CompensationStatus {
    /** 보정 대기 — 재시도 가능 */
    PENDING,
    /** 보정 완료 */
    RESOLVED,
    /** 최대 재시도 초과 — 운영자 수동 처리 필요 */
    MANUAL_REQUIRED
}
