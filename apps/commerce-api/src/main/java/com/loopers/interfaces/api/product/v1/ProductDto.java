package com.loopers.interfaces.api.product.v1;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.loopers.application.product.ProductCommand;
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
}
