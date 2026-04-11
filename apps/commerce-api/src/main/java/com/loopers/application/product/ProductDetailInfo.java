package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;

public record ProductDetailInfo(
        Long id,
        String name,
        String description,
        int price,
        int stockQuantity,
        String imageUrl,
        String brandName,
        long likeCount,
        Long rank
) {
    // 기존 팩토리 메서드 유지 (rank 없는 버전 — 목록 조회 등에서 사용)
    public static ProductDetailInfo of(ProductModel product, BrandModel brand, long likeCount) {
        return new ProductDetailInfo(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getImageUrl(),
                brand.getName(),
                likeCount,
                null
        );
    }

    // rank 포함 팩토리 메서드 (상세 조회에서 사용)
    public static ProductDetailInfo of(ProductModel product, BrandModel brand, long likeCount, Long rank) {
        return new ProductDetailInfo(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getImageUrl(),
                brand.getName(),
                likeCount,
                rank
        );
    }
}
