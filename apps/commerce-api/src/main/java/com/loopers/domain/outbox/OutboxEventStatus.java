package com.loopers.domain.outbox;

public enum OutboxEventStatus {
    PENDING,      // 발행 대기
    PUBLISHED,    // 발행 완료
    FAILED,       // 발행 실패 (재시도 예정)
    DEAD          // 최대 재시도 초과 (수동 조치 필요)
}
