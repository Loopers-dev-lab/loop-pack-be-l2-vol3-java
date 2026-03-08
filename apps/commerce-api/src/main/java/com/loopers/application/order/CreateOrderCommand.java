package com.loopers.application.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.List;
import java.util.Objects;

public record CreateOrderCommand(
    Long userId,
    List<CreateOrderCommand.LineItem> items,
    Long couponId
) {

    public CreateOrderCommand(Long userId, List<LineItem> items) {
        this(userId, items, null);
    }

    public CreateOrderCommand {
        Objects.requireNonNull(userId, "유저 ID는 필수입니다.");
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 하나 이상이어야 합니다.");
        }
    }

    public record LineItem(Long productId, int quantity) {
        public LineItem {
            Objects.requireNonNull(productId, "상품 ID는 필수입니다.");
            if (quantity < 1) {
                throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
            }
        }
    }
}
