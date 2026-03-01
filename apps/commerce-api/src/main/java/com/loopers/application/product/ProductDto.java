package com.loopers.application.product;

import com.loopers.domain.product.Product;

import java.math.BigDecimal;

public class ProductDto {

    public record ProductInfo(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        Integer likeCount,
        boolean isDeleted
    ) {
        public static ProductInfo of(Product product, String brandName) {
            return new ProductInfo(
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getLikeCount(),
                product.isDeleted()
            );
        }
    }
}
