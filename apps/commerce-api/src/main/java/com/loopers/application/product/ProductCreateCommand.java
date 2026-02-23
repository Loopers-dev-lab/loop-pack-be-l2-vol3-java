package com.loopers.application.product;

public record ProductCreateCommand(
        Long brandId,
        String name,
        String description,
        Integer price,
        Integer stockQuantity
) {}
