package com.loopers.support.enums;

/**
 * 결제 상태 머신. REQUESTED에서만 SUCCESS, FAILED, CANCELLED로 전이 가능.
 */
public enum PaymentStatus {
    /** PG에 결제 요청함, 결과 대기 중 */
    REQUESTED,
    /** PG 결제 성공 확인 */
    SUCCESS,
    /** PG 결제 실패 확인 */
    FAILED,
    /** 주문 취소/만료로 인한 내부 취소 */
    CANCELLED;

    /**
     * 최종 상태(더 이상 전이 불가)인지 판별한다.
     *
     * @return 최종 상태이면 true
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED;
    }

    /**
     * 현재 상태에서 대상 상태로 전이 가능한지 판별한다.
     * REQUESTED에서만 SUCCESS, FAILED, CANCELLED로 전이할 수 있다.
     *
     * @param target 전이 대상 상태
     * @return 전이 가능하면 true
     */
    public boolean canTransitionTo(PaymentStatus target) {
        if (this != REQUESTED) return false;
        return target == SUCCESS || target == FAILED || target == CANCELLED;
    }
}
