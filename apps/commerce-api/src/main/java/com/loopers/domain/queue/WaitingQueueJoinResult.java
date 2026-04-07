package com.loopers.domain.queue;

/**
 * 대기열 ZSET에 멤버를 넣으려 한 결과(원자적 add·정원 검사).
 */
public enum WaitingQueueJoinResult {

    /** 신규 멤버로 추가됨 */
    ADDED,

    /** 동일 userId가 이미 대기 중 */
    ALREADY_MEMBER,

    /** 정원(maxWaiting)에 도달해 신규 진입 불가 */
    CAPACITY_FULL
}
