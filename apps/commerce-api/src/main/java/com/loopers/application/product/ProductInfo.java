package com.loopers.application.product;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

import java.math.BigDecimal;

public record ProductInfo(
        Long id,
        String productId,
        Long refBrandId,
        String productName,
        BigDecimal price,
        int stockQuantity,
        BrandInfo brand,
        long likesCount,
        Long rank,
        Double rankingScore
) {
    public static ProductInfo from(ProductModel product, BrandModel brand, long likesCount) {
        return new ProductInfo(
                product.getId(),
                product.getProductId().value(),
                product.getRefBrandId().value(),
                product.getProductName().value(),
                product.getPrice().value(),
                product.getStockQuantity().value(),
                BrandInfo.from(brand),
                likesCount,
                null,
                null
        );
    }

    public static ProductInfo from(ProductModel product) {
        return new ProductInfo(
                product.getId(),
                product.getProductId().value(),
                product.getRefBrandId().value(),
                product.getProductName().value(),
                product.getPrice().value(),
                product.getStockQuantity().value(),
                null,
                0L,
                null,
                null
        );
    }

    public ProductInfo enrich(BrandInfo brand, long likesCount) {
        return new ProductInfo(id, productId, refBrandId, productName, price, stockQuantity, brand, likesCount, rank, rankingScore);
    }

    public ProductInfo withRanking(Long rank, Double rankingScore) {
        return new ProductInfo(id, productId, refBrandId, productName, price, stockQuantity, brand, likesCount, rank, rankingScore);
    }
}
