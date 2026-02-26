package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;

import java.math.BigDecimal;

/**
 * 고객용 상품 API DTO.
 * Application의 ProductDetailInfo와 매핑하며, interfaces 전용으로 분리한다.
 */
public class ProductV1Dto {

    public record DetailResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        BigDecimal price,
        int stockQuantity,
        long likeCount
    ) {
        public static DetailResponse from(ProductDetailInfo info) {
            if (info == null) {
                return null;
            }
            return new DetailResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.stockQuantity(),
                info.likeCount()
            );
        }
    }
}
