package com.loopers.domain.payment;

public enum PaymentStatus {

    PENDING,    // PG 결제 결과 대기 중
    SUCCESS,    // 결제 성공
    FAILED,     // 결제 실패
    TIMEOUT;    // PG 응답 없음 (타임아웃)

    // PENDING → SUCCESS, FAILED, TIMEOUT 전이만 허용
    public boolean canTransitTo(PaymentStatus target) {
        return this == PENDING;
    }

    // 최종 상태 여부 (멱등성 보장에 사용)
    // 이미 처리된 데이터라면 불필요하게 다시 UPDATE가 실행되지 않게 방지
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT;
    }
}
