package com.loopers.domain.model.order;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderItem {

    private final Long id;
    private final Long productId;
    private final int quantity;
    private final Money unitPrice;

    public static OrderItem create(Long productId, int quantity, Money unitPrice) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다.");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("단가는 필수입니다.");
        }
        return new OrderItem(null, productId, quantity, unitPrice);
    }

    public static OrderItem reconstitute(Long id, Long productId, int quantity, Money unitPrice) {
        return new OrderItem(id, productId, quantity, unitPrice);
    }

    public Money calculateAmount() {
        return unitPrice.multiply(quantity);
    }
}
