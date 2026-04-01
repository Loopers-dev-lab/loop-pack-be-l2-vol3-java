package com.loopers.domain.payment;

public enum CompensationDlqStatus {
    PENDING,    // 재시도 대기
    COMPLETED,  // 보상 성공
    FAILED      // 최대 재시도 초과 — 수동 개입 필요
}
