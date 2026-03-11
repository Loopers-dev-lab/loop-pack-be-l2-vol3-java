package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;

/**
 * OrderItem 도메인
 */
public record OrderItem(Long id, Long refOrderId, Long refProductId, Integer quantity, Money price) {

    public static OrderItem create(Long id, Long refOrderId, Long refProductId, Integer quantity, Integer price) {
        validateRefOrderId(refOrderId);
        validateRefProductId(refProductId);
        validateQuantity(quantity);

        return new OrderItem(id, refOrderId, refProductId, quantity, new Money(price));
    }

    private static void validateRefOrderId(Long refOrderId) {
        // 새 Order 애그리거트 구성 시점에는 refOrderId가 아직 없을 수 있으므로
        // null 은 허용하고, 0 이하인 값만 검증 대상으로 본다.
        if (refOrderId != null && refOrderId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.ORDER_ID_INVALID);
        }
    }

    private static void validateRefProductId(Long refProductId) {
        if (refProductId == null || refProductId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.PRODUCT_ID_INVALID);
        }
    }

    private static void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Order.QUANTITY_MUST_BE_POSITIVE);
        }
    }
}
