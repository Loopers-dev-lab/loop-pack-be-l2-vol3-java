package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;

/**
 * OrderStatusHistory 도메인
 */
public record OrderStatusHistory(Long id, Long refOrderId, OrderStatus status, ZonedDateTime changedAt) {

    public static OrderStatusHistory create(Long id, Long refOrderId, OrderStatus status, ZonedDateTime changedAt) {
        validateRefOrderId(refOrderId);
        validateStatus(status);
        validateChangedAt(changedAt);

        return new OrderStatusHistory(id, refOrderId, status, changedAt);
    }

    private static void validateRefOrderId(Long refOrderId) {
        // 새 Order 애그리거트 구성 시점에는 refOrderId가 아직 없을 수 있으므로
        // null 은 허용하고, 0 이하인 값만 검증 대상으로 본다.
        if (refOrderId != null && refOrderId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_ID_INVALID);
        }
    }

    private static void validateStatus(OrderStatus status) {
        if (status == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_STATUS_REQUIRED);
        }
    }

    private static void validateChangedAt(ZonedDateTime changedAt) {
        if (changedAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_STATUS_CHANGE_DT_REQUIRED);
        }
    }
}
