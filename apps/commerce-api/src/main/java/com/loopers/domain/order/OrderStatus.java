package com.loopers.domain.order;

public enum OrderStatus {

    PENDING_PAYMENT,    // 결제 대기 (주문 생성 직후)
    PAID,               // 결제 완료
    PAYMENT_FAILED,     // 결제 실패
    PAYMENT_TIMEOUT;    // 결제 타임아웃

    // PENDING_PAYMENT → PAID, PAYMENT_FAILED, PAYMENT_TIMEOUT 전이만 허용
    public boolean canTransitTo(OrderStatus target) {
        return this == PENDING_PAYMENT;
    }
}
