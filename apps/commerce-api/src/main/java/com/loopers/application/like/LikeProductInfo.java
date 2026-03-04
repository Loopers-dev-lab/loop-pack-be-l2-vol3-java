package com.loopers.application.like;

import com.loopers.domain.product.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LikeProductInfo(
        Long id,
        Long brandId,
        String brandName,
        String name,
        BigDecimal price,
        Integer likeCount,
        LocalDateTime createdAt
) {

    public static LikeProductInfo from(Product product, String brandName) {
        return new LikeProductInfo(
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName(),
                product.getPrice(),
                product.getLikeCount(),
                product.getCreatedAt().toLocalDateTime()
        );
    }
}
