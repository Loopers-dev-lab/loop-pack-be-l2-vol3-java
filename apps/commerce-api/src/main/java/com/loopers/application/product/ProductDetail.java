package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockStatus;

import java.time.ZonedDateTime;

public record ProductDetail(
    Long id,
    String name,
    String description,
    int price,
    Long brandId,
    String brandName,
    int likeCount,
    StockStatus stockStatus,
    int stockQuantity,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    ZonedDateTime deletedAt
) {

    public static ProductDetail ofCustomer(ProductModel product, String brandName, StockStatus stockStatus) {
        return new ProductDetail(
            product.getId(), product.getName(), product.getDescription(),
            product.getPrice().value(), product.getBrandId(), brandName,
            product.getLikeCount(), stockStatus, 0,
            product.getCreatedAt(), product.getUpdatedAt(), product.getDeletedAt()
        );
    }

    public static ProductDetail ofAdmin(ProductModel product, String brandName, int stockQuantity) {
        return new ProductDetail(
            product.getId(), product.getName(), product.getDescription(),
            product.getPrice().value(), product.getBrandId(), brandName,
            product.getLikeCount(), null, stockQuantity,
            product.getCreatedAt(), product.getUpdatedAt(), product.getDeletedAt()
        );
    }
}
