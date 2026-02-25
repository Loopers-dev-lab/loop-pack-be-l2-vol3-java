package com.loopers.domain.order;

import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Objects;

public record OrderItemCommand(
    Long productId,
    String productName,
    Money productPrice,
    String brandName,
    int quantity
) {

    public OrderItemCommand {
        Objects.requireNonNull(productId, "상품 ID는 필수입니다.");
        if (productName == null || productName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 이름은 필수입니다.");
        }
        Objects.requireNonNull(productPrice, "상품 가격은 필수입니다.");
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.");
        }
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "수량은 1 이상이어야 합니다.");
        }
    }
}
