package com.loopers.interfaces.api.product;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class AdminProductV1Dto {

    public record CreateRequest(
        @NotNull(message = "브랜드 ID는 필수입니다.")
        Long brandId,

        @NotBlank(message = "상품 이름은 비어있을 수 없습니다.")
        String name,

        @NotNull(message = "상품 가격은 필수입니다.")
        @Min(value = 0, message = "상품 가격은 0 이상이어야 합니다.")
        Integer price,

        @NotNull(message = "상품 재고는 필수입니다.")
        @Min(value = 0, message = "상품 재고는 0 이상이어야 합니다.")
        Integer stock
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "상품 이름은 비어있을 수 없습니다.")
        String name,

        @NotNull(message = "상품 가격은 필수입니다.")
        @Min(value = 0, message = "상품 가격은 0 이상이어야 합니다.")
        Integer price,

        @NotNull(message = "상품 재고는 필수입니다.")
        @Min(value = 0, message = "상품 재고는 0 이상이어야 합니다.")
        Integer stock
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        int price,
        int stock,
        int likeCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static ProductResponse from(Product product, Brand brand) {
            return new ProductResponse(
                product.getId(), product.getBrandId(), brand.getName(), product.getName(),
                product.getPrice().amount(), product.getStock().quantity(), product.getLikeCount(),
                product.getCreatedAt(), product.getUpdatedAt()
            );
        }
    }

    public record ProductPageResponse(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static ProductPageResponse from(PageResult<Product> result, Map<Long, Brand> brandMap) {
            List<ProductResponse> content = result.items().stream()
                .map(product -> ProductResponse.from(product, brandMap.get(product.getBrandId())))
                .toList();
            return new ProductPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
