package com.loopers.application.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.List;

public class OrderItemValidator {

    private OrderItemValidator() {}

    public static void validate(List<OrderItemCommand> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }
        if (items.stream().anyMatch(item -> item.quantity() <= 0)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }
        long distinctCount = items.stream().map(OrderItemCommand::productId).distinct().count();
        if (distinctCount != items.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "중복된 상품이 포함되어 있습니다.");
        }
    }
}
