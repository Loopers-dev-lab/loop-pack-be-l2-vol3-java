package com.loopers.domain.idempotency;

public enum EventLogStatus {
    SUCCESS,    // 정상 처리
    SKIPPED,    // 멱등 처리로 skip (이미 event_handled에 존재)
    FAILED      // 비즈니스 로직 실패 (파싱 오류, 데이터 오류 등)
}
