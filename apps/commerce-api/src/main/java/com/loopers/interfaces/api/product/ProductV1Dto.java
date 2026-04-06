package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import org.springframework.data.domain.Page;

import java.util.List;

public class ProductV1Dto {

    public record ProductResponse(
        Long productId,
        Long brandId,
        String name,
        Long price,
        String description,
        Integer stockQuantity,
        long likeCount
    ) {

        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.productId(),
                info.brandId(),
                info.name(),
                info.price(),
                info.description(),
                info.stockQuantity(),
                info.likeCount()
            );
        }
    }

    public record ProductListResponse(
        List<ProductResponse> products,
        PageInfo page
    ) {

        public static ProductListResponse from(Page<ProductInfo> pageResult) {
            List<ProductResponse> products = pageResult.getContent().stream()
                .map(ProductResponse::from)
                .toList();
            PageInfo pageInfo = new PageInfo(
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
            );
            return new ProductListResponse(products, pageInfo);
        }
    }

    public record PageInfo(
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {}
}
