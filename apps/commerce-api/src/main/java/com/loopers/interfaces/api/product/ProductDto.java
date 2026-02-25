package com.loopers.interfaces.api.product;

import com.loopers.application.product.command.CreateProductCommand;
import com.loopers.domain.product.Product;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.util.List;

public class ProductDto {

    public record CreateProductRequest(
            @NotBlank(message = "상품명은 필수입니다")
            String name,
            @NotNull(message = "가격은 필수입니다")
            @Min(value = 0, message = "가격은 0 이상이어야 합니다")
            Integer price,
            @NotNull(message = "재고는 필수입니다")
            @Min(value = 0, message = "재고는 0 이상이어야 합니다")
            Integer stock,
            String description,
            @NotNull(message = "카테고리 ID는 필수입니다")
            Long categoryId,
            @NotNull(message = "브랜드 ID는 필수입니다")
            Long brandId
    ) {
        public CreateProductCommand toCommand() {
            return new CreateProductCommand(name, price, stock, description, categoryId, brandId);
        }
    }

    public record ProductResponse(
            Long id,
            String name,
            Integer price,
            Integer stock,
            String description,
            Long categoryId,
            Long brandId,
            Integer likeCount
    ) {
        public static ProductResponse from(Product product) {
            return new ProductResponse(
                    product.id(),
                    product.name(),
                    product.price(),
                    product.stock(),
                    product.description(),
                    product.categoryId(),
                    product.brandId(),
                    product.likeCount()
            );
        }
    }

    public record ProductListResponse(
            List<ProductResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static ProductListResponse from(Page<Product> pageData) {
            List<ProductResponse> items = pageData.getContent().stream()
                    .map(ProductResponse::from)
                    .toList();
            return new ProductListResponse(
                    items,
                    pageData.getNumber(),
                    pageData.getSize(),
                    pageData.getTotalElements(),
                    pageData.getTotalPages()
            );
        }
    }
}
