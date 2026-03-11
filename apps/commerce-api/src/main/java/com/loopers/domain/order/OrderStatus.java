package com.loopers.domain.order;

public enum OrderStatus {

    ORDERED("주문 완료"),    // 주문 완료
    CANCELLED("주문 취소");   // 주문 취소

    private final String message;

    OrderStatus(String message) {
        this.message = message;
    }
}
