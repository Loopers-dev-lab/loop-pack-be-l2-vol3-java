package com.loopers.application.product;

import org.springframework.util.Assert;

import java.util.Objects;

public record RegisterProductCommand(Long brandId, String name, int price, int stock) {

    public RegisterProductCommand {
        Objects.requireNonNull(brandId, "브랜드 ID는 필수입니다.");
        Assert.hasText(name, "상품 이름은 비어있을 수 없습니다.");
        Assert.state(price >= 0, "상품 가격은 0 이상이어야 합니다.");
        Assert.state(stock >= 0, "상품 재고는 0 이상이어야 합니다.");
    }
}
