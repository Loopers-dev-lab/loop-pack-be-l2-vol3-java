package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import org.springframework.data.domain.Page;

import java.util.List;

public class ProductV1Dto {

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        int price,
        int stock,
        String sellingStatus,
        int likeCount,
        Boolean isLiked,
        Integer rank
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.sellingStatus().name(),
                info.likeCount(),
                info.isLiked(),
                info.rank()
            );
        }
    }

    public record ProductPageResponse(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements
    ) {
        public static ProductPageResponse from(Page<ProductInfo> page) {
            List<ProductResponse> content = page.getContent().stream()
                .map(ProductResponse::from)
                .toList();
            return new ProductPageResponse(content, page.getNumber(), page.getSize(), page.getTotalElements());
        }
    }
}
