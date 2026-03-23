package com.loopers.domain.payment;

public enum PaymentStatus {
    PENDING,   // 결제 요청 접수, PG 처리 대기 중
    SUCCESS,   // 결제 성공
    FAILED,    // 결제 실패
    REFUNDED   // 환불 완료 (주문 취소로 인한 내부 처리)
}
