package com.loopers.application.product.command;

public record CreateProductCommand(
        String name,
        Integer price,
        Integer stock,
        String description,
        Long categoryId,
        Long brandId
) {
}
