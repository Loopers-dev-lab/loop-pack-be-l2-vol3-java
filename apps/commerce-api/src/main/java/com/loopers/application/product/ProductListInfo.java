package com.loopers.application.product;

import com.loopers.domain.product.Brand;
import com.loopers.domain.product.Product;

import java.util.Map;

public record ProductListInfo(
    Long id,
    String name,
    Long price,
    String brandName,
    long likeCount
) {
    public static ProductListInfo of(Product product, Brand brand, long likeCount) {
        String brandName = brand != null ? brand.getName() : "";
        return new ProductListInfo(
            product.getId(),
            product.getName(),
            product.getPrice(),
            brandName,
            likeCount
        );
    }

    public static ProductListInfo of(Product product, Map<Long, Brand> brandMap, Map<Long, Long> likeCountMap) {
        Brand brand = product.getBrandId() != null ? brandMap.get(product.getBrandId()) : null;
        long likeCount = likeCountMap.getOrDefault(product.getId(), 0L);
        return of(product, brand, likeCount);
    }
}
