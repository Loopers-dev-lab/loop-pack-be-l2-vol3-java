package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductPageResult;

import java.util.List;

public class ProductV1Dto {

    public record ProductResponse(
            long id,
            String brandName,
            String name,
            int price,
            int stock,
            int likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.stock(),
                    info.likeCount()
            );
        }
    }

    public record ProductListResponse(
            List<ProductResponse> products,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static ProductListResponse from(ProductPageResult result) {
            return new ProductListResponse(
                    result.products().stream().map(ProductResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }
}
