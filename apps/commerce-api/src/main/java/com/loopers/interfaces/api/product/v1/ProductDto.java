package com.loopers.interfaces.api.product.v1;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductDetail;
import com.loopers.application.product.ProductResult;

public class ProductDto {

    public record CreateProductRequest(
            @NotNull(message = "브랜드 ID는 필수입니다.") Long brandId,
            @NotBlank(message = "상품명은 필수입니다.") String name,
            @NotBlank(message = "썸네일 URL은 필수입니다.") String thumbnailUrl,
            @NotNull(message = "가격은 필수입니다.") Long price,
            @NotNull(message = "재고는 필수입니다.") Long stock,
            String description
    ) {

        public ProductCommand.CreateProductCommand toCreateProductCommand() {
            return new ProductCommand.CreateProductCommand(
                    brandId,
                    name,
                    thumbnailUrl,
                    price,
                    stock,
                    description
            );
        }
    }

    public record CreateProductResponse(Long productId) {

        public static CreateProductResponse from(Long productId) {
            return new CreateProductResponse(productId);
        }
    }

    public record UpdateProductRequest(
            @NotBlank(message = "상품명은 필수입니다.") String name,
            @NotBlank(message = "썸네일 URL은 필수입니다.") String thumbnailUrl,
            @NotNull(message = "가격은 필수입니다.") Long price,
            @NotNull(message = "재고는 필수입니다.") Long stock,
            String description
    ) {

        public ProductCommand.UpdateProductCommand toUpdateProductCommand(Long productId) {
            return new ProductCommand.UpdateProductCommand(
                    productId,
                    name,
                    thumbnailUrl,
                    price,
                    stock,
                    description
            );
        }
    }

    public record ProductResponse(
            Long id,
            Long brandId,
            String name,
            String thumbnailUrl,
            Long price,
            Long stock,
            String description
    ) {

        public static ProductResponse from(ProductResult result) {
            return new ProductResponse(
                    result.id(),
                    result.brandId(),
                    result.name(),
                    result.thumbnailUrl(),
                    result.price(),
                    result.stock(),
                    result.description()
            );
        }

        public static List<ProductResponse> from(List<ProductResult> results) {
            return results.stream()
                    .map(ProductResponse::from)
                    .toList();
        }
    }

    public record ProductDetailResponse(
            Long productId,
            String name,
            String thumbnailUrl,
            Long price,
            Long stock,
            String description,
            BrandInfo brand,
            Long likeCount,
            boolean liked
    ) {

        public static ProductDetailResponse from(ProductDetail productDetail) {
            return new ProductDetailResponse(
                    productDetail.productId(),
                    productDetail.name(),
                    productDetail.thumbnailUrl(),
                    productDetail.price(),
                    productDetail.stock(),
                    productDetail.description(),
                    BrandInfo.from(productDetail),
                    productDetail.likeCount(),
                    productDetail.liked()
            );
        }

        public record BrandInfo(
                Long id,
                String name,
                String logoUrl
        ) {

            public static BrandInfo from(ProductDetail productDetail) {
                return new BrandInfo(
                        productDetail.brandId(),
                        productDetail.brandName(),
                        productDetail.brandLogoUrl()
                );
            }
        }
    }
}
