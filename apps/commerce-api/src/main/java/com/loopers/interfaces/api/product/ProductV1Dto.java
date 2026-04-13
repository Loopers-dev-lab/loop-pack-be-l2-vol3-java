package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDto;
import com.loopers.application.ranking.RankingDto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

public class ProductV1Dto {

    public record CreateRequest(
        @NotNull(message = "브랜드 ID는 필수입니다")
        Long brandId,

        @NotBlank(message = "상품명은 필수입니다")
        @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
        String name,

        @Size(max = 2000, message = "상품 설명은 2000자 이하여야 합니다")
        String description,

        @NotNull(message = "상품 가격은 필수입니다")
        @DecimalMin(value = "0.0", inclusive = true, message = "상품 가격은 0 이상이어야 합니다")
        BigDecimal price,

        @NotNull(message = "재고는 필수입니다")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다")
        Integer stock
    ) {
    }

    public record UpdateRequest(
        @NotBlank(message = "상품명은 필수입니다")
        @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
        String name,

        @Size(max = 2000, message = "상품 설명은 2000자 이하여야 합니다")
        String description,

        @NotNull(message = "상품 가격은 필수입니다")
        @DecimalMin(value = "0.0", inclusive = true, message = "상품 가격은 0 이상이어야 합니다")
        BigDecimal price,

        @NotNull(message = "재고는 필수입니다")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다")
        Integer stock
    ) {
    }

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        Integer likeCount,
        boolean isDeleted
    ) {
        public static ProductResponse from(ProductDto.ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.likeCount(),
                info.isDeleted()
            );
        }
    }

    public record ProductDetailResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        Integer likeCount,
        boolean isDeleted,
        Long rank,
        Double rankScore
    ) {
        public static ProductDetailResponse of(ProductDto.ProductInfo info, RankingDto.ProductRankInfo rankInfo) {
            return new ProductDetailResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.description(),
                info.price(),
                info.stock(),
                info.likeCount(),
                info.isDeleted(),
                rankInfo.rank(),
                rankInfo.score()
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
        public static ProductPageResponse from(Page<ProductDto.ProductInfo> page) {
            return new ProductPageResponse(
                page.getContent().stream().map(ProductResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
