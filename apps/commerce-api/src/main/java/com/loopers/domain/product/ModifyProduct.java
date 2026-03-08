package com.loopers.domain.product;

public record ModifyProduct(
        Long productId,
        String name,
        String thumbnailUrl,
        Long price,
        Long stock,
        String description
) {
}
