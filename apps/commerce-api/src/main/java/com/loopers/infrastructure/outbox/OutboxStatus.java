package com.loopers.infrastructure.outbox;

public enum OutboxStatus {
    PENDING,      // 발행 대기
    PROCESSING,   // 발행 중 (멀티 인스턴스 중복 방지)
    PUBLISHED,    // 발행 완료
    FAILED        // 발행 실패
}
