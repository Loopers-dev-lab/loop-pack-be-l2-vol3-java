package com.loopers.application.product;

public record ProductDetailInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    Integer price,
    Integer stock,
    Integer likesCount,
    String description,
    String imageUrl,
    Long rank
) {
    public static ProductDetailInfo of(ProductInfo info, Long rank) {
        return new ProductDetailInfo(
            info.id(),
            info.brandId(),
            info.brandName(),
            info.name(),
            info.price(),
            info.stock(),
            info.likesCount(),
            info.description(),
            info.imageUrl(),
            rank
        );
    }
}
