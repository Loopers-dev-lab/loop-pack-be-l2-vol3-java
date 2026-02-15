package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.PageResult;

import java.util.List;

public class ProductV1Dto {

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        int price,
        int likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(), info.brandId(), info.brandName(), info.name(),
                info.price(), info.likeCount()
            );
        }
    }

    public record ProductPageResponse(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static ProductPageResponse from(PageResult<ProductInfo> result) {
            List<ProductResponse> content = result.items().stream()
                .map(ProductResponse::from)
                .toList();
            return new ProductPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
