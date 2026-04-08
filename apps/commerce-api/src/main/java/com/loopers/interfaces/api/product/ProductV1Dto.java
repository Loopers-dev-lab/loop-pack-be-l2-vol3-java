package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductListInfo;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public class ProductV1Dto {

    public record ProductDetailResponse(
        Long productId,
        String name,
        String description,
        BrandSummary brand,
        List<String> imageUrls,
        List<OptionResponse> options,
        long likeCount,
        ZonedDateTime createdAt,
        Integer ranking
    ) {
        public static ProductDetailResponse from(ProductDetailInfo info, Integer ranking) {
            return new ProductDetailResponse(
                info.productId(),
                info.name(),
                info.description(),
                new BrandSummary(info.brand().brandId(), info.brand().name()),
                info.imageUrls(),
                info.options().stream()
                    .map(o -> new OptionResponse(o.optionId(), o.name(), o.price(), o.stockQuantity(), o.isAvailable()))
                    .toList(),
                info.likeCount(),
                info.createdAt(),
                ranking
            );
        }
    }

    public record BrandSummary(Long brandId, String name) {}

    public record OptionResponse(Long optionId, String name, Long price, Long stockQuantity, boolean isAvailable) {}

    public record ProductListItemResponse(
        Long productId,
        String name,
        BrandSummary brand,
        String thumbnailImageUrl,
        Long minPrice,
        long likeCount,
        ZonedDateTime createdAt
    ) {
        public static ProductListItemResponse from(ProductListInfo info) {
            return new ProductListItemResponse(
                info.productId(),
                info.name(),
                new BrandSummary(info.brandId(), info.brandName()),
                info.thumbnailImageUrl(),
                info.minPrice(),
                info.likeCount(),
                info.createdAt()
            );
        }
    }

    public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}