package com.loopers.domain.order;

import java.util.Objects;

import org.springframework.util.Assert;

public record OrderLineItem(Long productId, int quantity) {

    public OrderLineItem {
        Objects.requireNonNull(productId, "상품 ID는 필수입니다.");
        Assert.state(quantity >= 1, "수량은 1 이상이어야 합니다.");
    }
}
