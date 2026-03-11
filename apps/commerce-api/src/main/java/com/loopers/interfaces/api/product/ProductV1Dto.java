package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProductV1Dto {

    public record CreateProductRequest(
        @NotEmpty(message = "상품 목록은 필수입니다")
        @Valid
        List<ProductItemRequest> products
    ) {
        public Map<Long, com.loopers.application.product.CreateProductCommand.ProductItem> toProductItems() {
            return products.stream()
                .collect(Collectors.toMap(
                    ProductItemRequest::brandId,
                    item -> new com.loopers.application.product.CreateProductCommand.ProductItem(
                        item.name(),
                        item.price(),
                        item.stock()
                    )
                ));
        }
    }

    public record ProductItemRequest(
        @NotNull(message = "브랜드 ID는 필수입니다")
        Long brandId,

        @NotBlank(message = "상품명은 필수입니다")
        String name,

        @NotNull(message = "가격은 필수입니다")
        @Min(value = 0, message = "가격은 0원 이상이어야 합니다")
        Integer price,

        @NotNull(message = "재고는 필수입니다")
        @Min(value = 0, message = "재고는 0개 이상이어야 합니다")
        Integer stock
    ) {
    }

    public record CreateProductResponse(
        List<ProductResponse> products
    ) {
        public static CreateProductResponse from(List<ProductInfo> productInfos) {
            List<ProductResponse> products = productInfos.stream()
                .map(ProductResponse::from)
                .toList();
            return new CreateProductResponse(products);
        }
    }

    public record ProductResponse(
        Long id,
        String name,
        Integer price,
        Integer stock,
        Integer likeCount
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.name(),
                info.price(),
                info.stock(),
                info.likeCount()
            );
        }
    }

    public record ProductDetailResponse(
        Long id,
        String name,
        Integer price,
        Integer stock,
        Integer likeCount,
        Long brandId,
        String brandName,
        String brandDescription
    ) {
        public static ProductDetailResponse from(ProductInfo info) {
            return new ProductDetailResponse(
                info.id(),
                info.name(),
                info.price(),
                info.stock(),
                info.likeCount(),
                info.brandId(),
                info.brandName(),
                info.brandDescription()
            );
        }
    }
}
