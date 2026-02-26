package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class ProductV1Dto {

    public record RegisterRequest(
            Long brandId,
            String name,
            String description,
            BigDecimal price,
            Integer stock,
            String imageUrl
    ) {}

    public record UpdateRequest(
            String name,
            String description,
            BigDecimal price,
            Integer stock,
            String imageUrl
    ) {}

    public record Response(
            Long id,
            Long brandId,
            String brandName,
            String name,
            String description,
            BigDecimal price,
            Integer stock,
            String imageUrl,
            Integer likesCount,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static Response from(ProductInfo info) {
            return new Response(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.description(),
                    info.price(),
                    info.stock(),
                    info.imageUrl(),
                    info.likesCount(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record PageResponse(
            List<Response> content,
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static PageResponse from(org.springframework.data.domain.Page<ProductInfo> page) {
            List<Response> content = page.getContent().stream()
                    .map(Response::from)
                    .toList();
            
            return new PageResponse(
                    content,
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }
}
