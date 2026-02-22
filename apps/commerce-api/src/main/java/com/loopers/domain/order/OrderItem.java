package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class OrderItem {
    private final Long productId;
    private final String productName;
    private final Money price;
    private final int quantity;

    private OrderItem(Long productId, String productName, Money price, int quantity) {
        validate(productId, productName, price, quantity);
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderItem of(Long productId, String productName, Money price, int quantity) {
        return new OrderItem(productId, productName, price, quantity);
    }

    public Money getTotalPrice() {
        return price.multiply(quantity);
    }

    private void validate(Long productId, String productName, Money price, int quantity) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다.");
        }
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }
}
