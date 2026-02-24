package com.loopers.domain.order;

import com.loopers.domain.product.Money;

import java.util.Objects;

import org.springframework.util.Assert;

public record OrderItemCommand(
    Long productId,
    String productName,
    Money productPrice,
    String brandName,
    int quantity
) {

    public OrderItemCommand {
        Objects.requireNonNull(productId, "상품 ID는 필수입니다.");
        Assert.hasText(productName, "상품 이름은 필수입니다.");
        Objects.requireNonNull(productPrice, "상품 가격은 필수입니다.");
        Assert.hasText(brandName, "브랜드 이름은 필수입니다.");
        Assert.state(quantity >= 1, "수량은 1 이상이어야 합니다.");
    }
}
