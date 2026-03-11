package com.loopers.domain.product;

public record CreateProductRequest(
    String name,
    Integer price,
    Integer stock
) {
}
