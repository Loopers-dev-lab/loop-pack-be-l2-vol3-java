package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetail;
import com.loopers.domain.stock.StockStatus;

public class ProductV1Dto {

    public record ProductSummaryResponse(
        Long id, String name, int price, String brandName, String stockStatus
    ) {
        public static ProductResponse from(ProductDetail detail) {
            return new ProductResponse(
                detail.id(),
                detail.name(),
                detail.description(),
                detail.price(),
                detail.brandId(),
                detail.brandName(),
                detail.likeCount(),
                detail.stockStatus()
            );
        }
    }
}
