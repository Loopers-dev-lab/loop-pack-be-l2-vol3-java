package com.loopers.interfaces.api.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductWithBrand;
import com.loopers.interfaces.api.ranking.RankingDto;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.util.List;

public class ProductDto {

    public record CreateRequest(
        @NotNull Long brandId,
        @NotBlank String name,
        @Min(0) int price,
        @Min(0) int stockQuantity,
        Long categoryId
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
        int likeCount,
        Long categoryId,
        RankingDto.RankingInfo ranking
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
                (int) info.likeCount(),
                product.getCategoryId(),
                null
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
                0,
                product.getCategoryId(),
                null
            );
        }

        public ProductResponse withRanking(RankingDto.RankingInfo ranking) {
            return new ProductResponse(id, brandId, brandName, name, price, stockQuantity, likeCount, categoryId, ranking);
        }
    }

    public record PagedProductResponse(
        List<ProductResponse> data,
        long totalElements,
        int totalPages,
        int page,
        int size
    ) {
        public static PagedProductResponse from(Page<ProductWithBrand> pageResult) {
            List<ProductResponse> data = pageResult.getContent().stream()
                .map(ProductResponse::from)
                .toList();
            return new PagedProductResponse(
                data,
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.getNumber(),
                pageResult.getSize()
            );
        }
    }
}
