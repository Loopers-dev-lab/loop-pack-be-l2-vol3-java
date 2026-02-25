package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

import java.math.BigDecimal;

/**
 * 상품 응답용 애플리케이션 DTO.
 * Controller 응답에 사용하며, interfaces DTO와 분리한다.
 */
public record ProductInfo(
    Long id,
    Long brandId,
    String name,
    BigDecimal price,
    int stockQuantity,
    boolean deleted
) {
    public static ProductInfo from(ProductModel product) {
        if (product == null) {
            return null;
        }
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getPrice(),
            product.getStockQuantity(),
            product.isDeleted()
        );
    }
}
