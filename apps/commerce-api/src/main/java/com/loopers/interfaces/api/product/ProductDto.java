package com.loopers.interfaces.api.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductWithBrand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProductDto {

    public record CreateRequest(
        @NotNull Long brandId,
        @NotBlank String name,
        @Min(0) int price,
        @Min(0) int stockQuantity
    ) {}

    public record UpdateRequest(
        @NotBlank String name,
        @Min(0) int price,
        @Min(0) int stockQuantity
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        int price,
        int stockQuantity,
        int likeCount
    ) {
        public static ProductResponse from(ProductWithBrand info) {
            Product product = info.product();
            return new ProductResponse(
                product.getId(),
                product.getBrandId(),
                info.brandName(),
                product.getName(),
                product.getPrice().getValue(),
                product.getStock().getQuantity(),
                product.getLikeCount()
            );
        }

        public static ProductResponse from(Product product) {
            return new ProductResponse(
                product.getId(),
                product.getBrandId(),
                null,
                product.getName(),
                product.getPrice().getValue(),
                product.getStock().getQuantity(),
                product.getLikeCount()
            );
        }
    }
}
