package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum OrderStatus {
    PENDING,    // 주문 대기
    PAID,       // 결제 완료
    CANCELED;   // 주문 취소

    public boolean canTransitionTo(OrderStatus newStatus) {
        return switch (this) {
            case PENDING -> newStatus == PAID || newStatus == CANCELED;
            case PAID -> false;
            case CANCELED -> newStatus == CANCELED;
        };
    }

    public void validateTransition(OrderStatus newStatus) {
        if (!canTransitionTo(newStatus)) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    String.format("주문 상태를 %s에서 %s로 변경할 수 없습니다.", this, newStatus)
            );
        }
    }
}
