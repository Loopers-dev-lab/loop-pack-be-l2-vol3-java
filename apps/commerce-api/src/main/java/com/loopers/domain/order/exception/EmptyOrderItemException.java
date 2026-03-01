package com.loopers.domain.order.exception;

public class EmptyOrderItemException extends RuntimeException {
    public EmptyOrderItemException() {
        super("주문 항목은 1개 이상이어야 합니다");
    }
}
