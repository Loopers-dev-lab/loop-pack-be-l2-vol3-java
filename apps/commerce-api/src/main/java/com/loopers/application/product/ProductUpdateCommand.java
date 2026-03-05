package com.loopers.application.product;

import com.loopers.domain.product.Money;
import com.loopers.domain.product.Stock;

// 소속 브랜드는 수정 불가 (BR-P02)
public record ProductUpdateCommand(
        Long id,
        String name,
        Money price,
        Stock stock
) {
}
