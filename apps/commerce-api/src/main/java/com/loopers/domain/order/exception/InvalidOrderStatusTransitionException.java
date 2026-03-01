package com.loopers.domain.order.exception;

import com.loopers.domain.order.OrderStatus;

public class InvalidOrderStatusTransitionException extends RuntimeException {
    public InvalidOrderStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("주문 상태를 변경할 수 없습니다. " + from + " -> " + to);
    }
}
