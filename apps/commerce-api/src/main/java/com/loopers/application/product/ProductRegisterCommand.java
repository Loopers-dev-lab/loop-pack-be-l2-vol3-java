package com.loopers.application.product;

import com.loopers.domain.product.Money;
import com.loopers.domain.product.Stock;

public record ProductRegisterCommand(
        Long brandId,
        String name,
        Money price,
        Stock stock
) {
}
