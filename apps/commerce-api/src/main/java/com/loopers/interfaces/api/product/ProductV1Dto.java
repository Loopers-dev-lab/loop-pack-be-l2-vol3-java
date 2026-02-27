package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import org.springframework.data.domain.Page;

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
        public static ProductListResponse from(Page<ProductInfo> info) {
            return new ProductListResponse(
                    info.getContent().stream().map(ProductResponse::from).toList(),
                    info.getNumber(),
                    info.getSize(),
                    info.getTotalElements(),
                    info.getTotalPages()
            );
        }
    }
}
