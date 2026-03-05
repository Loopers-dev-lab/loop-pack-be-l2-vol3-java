package com.loopers.domain.product;

import java.math.BigDecimal;

public record UpdateProductCommand(
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String imageUrl
) {}
