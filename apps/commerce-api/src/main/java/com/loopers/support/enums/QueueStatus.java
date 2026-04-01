package com.loopers.support.enums;

/**
 * 대기열에서 유저의 현재 상태. 순번 조회 API(GET /queue/position) 응답에 포함.
 *
 * <p>기존 프로젝트의 {@link OrderStatus#canCancel()}, {@link UnavailableReason#evaluate} 패턴을
 * 따라 상태별 헬퍼 메서드와 조건 기반 정적 팩토리를 제공한다.</p>
 */
public enum QueueStatus {
    /** 대기열에서 순번을 기다리는 중 — 순번과 예상 대기 시간 표시 */
    WAITING,
    /** 입장 토큰이 발급됨 — 즉시 주문 가능, 토큰값 포함 */
    READY,
    /** 대기열에 등록되어 있지 않음 — 진입 안내 표시 */
    NOT_IN_QUEUE;

    /**
     * 대기열 존재 여부와 토큰 존재 여부로 상태를 판단한다.
     *
     * <p>판단 우선순위: 토큰 있음 → READY (대기열에도 있으면 비정상이지만 READY 취급),
     * 대기열에 있음 → WAITING, 둘 다 없음 → NOT_IN_QUEUE</p>
     *
     * @param inQueue  대기열에 등록되어 있는지 (ZRANK != null)
     * @param hasToken 입장 토큰이 존재하는지 (GET entry-token:{userId} != null)
     * @return 현재 대기 상태
     */
    public static QueueStatus evaluate(boolean inQueue, boolean hasToken) {
        if (hasToken) return READY;
        if (inQueue) return WAITING;
        return NOT_IN_QUEUE;
    }

    /** READY 상태인지 확인 — 토큰으로 주문 가능 여부 */
    public boolean canOrder() {
        return this == READY;
    }

    /** 클라이언트에 Polling 계속 필요한 상태인지 */
    public boolean shouldPoll() {
        return this == WAITING;
    }
}
