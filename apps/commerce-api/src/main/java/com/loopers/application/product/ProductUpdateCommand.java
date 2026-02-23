package com.loopers.application.product;

import com.loopers.domain.product.Product;

public record ProductUpdateCommand(
        String name,
        String description,
        Integer price,
        Integer stockQuantity,
        Product.Visibility visibility
) {}
