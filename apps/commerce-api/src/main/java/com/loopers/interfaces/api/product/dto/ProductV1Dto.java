package com.loopers.interfaces.api.product.dto;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankingInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public class ProductV1Dto {

    public record ProductResponse(
            Long id,
            BrandInfo brand,
            String name,
            String description,
            Integer price,
            Integer stockQuantity,
            Integer likeCount,
            RankingSummary ranking
    ) {
        public record BrandInfo(String name) {}
        public record RankingSummary(Long rank, Double score) {}

        public static ProductResponse from(ProductInfo productInfo) {
            return from(productInfo, null);
        }

        public static ProductResponse from(ProductInfo productInfo, RankingInfo ranking) {
            RankingSummary rankingSummary = (ranking != null)
                    ? new RankingSummary(ranking.rank(), ranking.score())
                    : null;

            return new ProductResponse(
                    productInfo.id(),
                    new BrandInfo(productInfo.brand().name()),
                    productInfo.name(),
                    productInfo.description(),
                    productInfo.price(),
                    productInfo.stockQuantity(),
                    productInfo.likeCount(),
                    rankingSummary
            );
        }
    }

    public record AdminProductResponse(
            Long id,
            Long brandId,
            String name,
            String description,
            Integer price,
            Integer stockQuantity,
            Integer likeCount,
            Product.Visibility visibility,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static AdminProductResponse from(ProductInfo productInfo) {
            return new AdminProductResponse(
                    productInfo.id(),
                    productInfo.brand().id(),
                    productInfo.name(),
                    productInfo.description(),
                    productInfo.price(),
                    productInfo.stockQuantity(),
                    productInfo.likeCount(),
                    productInfo.visibility(),
                    productInfo.createdAt(),
                    productInfo.updatedAt()
            );
        }
    }

    public record CreateRequest(
            @NotNull Long brandId,
            @NotBlank(message = "상품 이름은 필수값입니다.") String name,
            String description,
            @NotNull Integer price,
            @NotNull Integer stockQuantity
    ) {}

    public record UpdateRequest(
            @NotBlank(message = "상품 이름은 필수값입니다.") String name,
            String description,
            @NotNull Integer price,
            @NotNull Integer stockQuantity,
            Product.Visibility visibility
    ) {}
}
