package com.loopers.domain.order;

import com.loopers.domain.product.Money;

public record OrderItemCommand(
    Long productId,
    String productName,
    Money productPrice,
    String brandName,
    int quantity
) {}
