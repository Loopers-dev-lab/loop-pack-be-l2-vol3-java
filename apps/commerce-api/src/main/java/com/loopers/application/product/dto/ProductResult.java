package com.loopers.application.product.dto;

import com.loopers.domain.product.ProductModel;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public record ProductResult(
    Long id,
    Long brandId,
    String brandName,
    String name,
    int price,
    int stock,
    long likeCount,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    ZonedDateTime deletedAt
) {
    public static ProductResult of(ProductModel model, String brandName) {
        return of(model, brandName, 0L);
    }

    public static ProductResult of(ProductModel model, String brandName, long likeCount) {
        return new ProductResult(
                model.getId(),
                model.getBrandId(),
                brandName,
                model.getName(),
                model.getPrice(),
                model.getStock(),
                likeCount,
                model.getCreatedAt(),
                model.getUpdatedAt(),
                model.getDeletedAt());
    }

    public static List<ProductResult> fromWithActiveBrand(
            List<ProductModel> products, Map<Long, String> brandNameMap,
            Map<Long, Long> likeCountMap) {
        return products.stream()
                .filter(product -> brandNameMap.containsKey(product.getBrandId()))
                .map(product -> ProductResult.of(
                        product,
                        brandNameMap.get(product.getBrandId()),
                        likeCountMap.getOrDefault(product.getId(), 0L)))
                .toList();
    }
}
