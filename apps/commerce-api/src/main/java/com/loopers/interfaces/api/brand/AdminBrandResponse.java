package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.product.ProductStatus;

import java.time.ZonedDateTime;
import java.util.List;

/** 브랜드 어드민 API 응답 DTO */
public class AdminBrandResponse {

    /** 브랜드 상세 정보 (Admin용 - 상태, 생성/수정 시각 포함) */
    public record BrandDetail(
            Long id,
            String name,
            String description,
            BrandStatus status,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static BrandDetail from(BrandInfo info) {
            return new BrandDetail(
                    info.id(), info.name(), info.description(),
                    info.status(), info.createdAt(), info.updatedAt()
            );
        }
    }

    /** 브랜드 목록 + 페이지네이션 메타 정보 */
    public record BrandListResponse(
            List<BrandDetail> brands,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    /** 브랜드 상세 + 상품 목록 (어드민용 - status 포함) */
    public record BrandDetailWithProducts(
            Long id,
            String name,
            String description,
            BrandStatus status,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt,
            List<ProductSummary> products
    ) {
        public static BrandDetailWithProducts from(BrandInfo brand, List<ProductInfo> products) {
            List<ProductSummary> productSummaries = products.stream()
                    .map(ProductSummary::from)
                    .toList();
            return new BrandDetailWithProducts(
                    brand.id(), brand.name(), brand.description(),
                    brand.status(), brand.createdAt(), brand.updatedAt(),
                    productSummaries
            );
        }
    }

    /** 브랜드 상세 내 상품 요약 (어드민용 - status 포함) */
    public record ProductSummary(
            Long id,
            String name,
            int basePrice,
            ProductStatus status,
            int likeCount
    ) {
        public static ProductSummary from(ProductInfo info) {
            return new ProductSummary(info.id(), info.name(), info.basePrice(), info.status(), info.likeCount());
        }
    }
}
