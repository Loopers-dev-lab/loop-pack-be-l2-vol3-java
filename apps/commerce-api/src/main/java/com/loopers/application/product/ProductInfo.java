package com.loopers.application.product;

import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;

public record ProductInfo(
        Long id,
        Long brandId,
        String brandName,
        String name,
        int price,
        int stock,
        int likeCount,
        Long rank,          // 오늘 기준 랭킹 순위 (없으면 null)
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static ProductInfo from(Product product, String brandName) {
        return new ProductInfo(
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName(),
                product.getPrice().getAmount(),
                product.getStock().getQuantity(),
                product.getLikeCount(),
                null,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public ProductInfo withRank(Long rank) {
        return new ProductInfo(id, brandId, brandName, name, price, stock, likeCount,
                rank, createdAt, updatedAt);
    }
}
