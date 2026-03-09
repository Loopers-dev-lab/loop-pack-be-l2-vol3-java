package com.loopers.domain.product;

import java.math.BigDecimal;

public record RegisterProductCommand(
        Long brandId,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String imageUrl
) {}
