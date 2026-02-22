package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductInfo;

import java.util.List;

/** 브랜드 API 응답 DTO (고객용) - 고객에게 노출하는 정보 최소화 */
public class BrandResponse {

    /** 브랜드 목록 조회용 요약 정보 */
    public record BrandSummary(
            Long id,
            String name,
            String description
    ) {
        public static BrandSummary from(BrandInfo info) {
            return new BrandSummary(info.id(), info.name(), info.description());
        }
    }

    /** 브랜드 상세 조회용 정보 (고객에게 createdAt 등 관리 필드 미노출) */
    public record BrandDetail(
            Long id,
            String name,
            String description
    ) {
        public static BrandDetail from(BrandInfo info) {
            return new BrandDetail(info.id(), info.name(), info.description());
        }
    }

    /** 브랜드 상세 + 상품 목록 (고객용) */
    public record BrandDetailWithProducts(
            Long id,
            String name,
            String description,
            List<ProductSummary> products
    ) {
        public static BrandDetailWithProducts from(BrandInfo brand, List<ProductInfo> products) {
            List<ProductSummary> productSummaries = products.stream()
                    .map(ProductSummary::from)
                    .toList();
            return new BrandDetailWithProducts(brand.id(), brand.name(), brand.description(), productSummaries);
        }
    }

    /** 브랜드 상세 내 상품 요약 (고객용) */
    public record ProductSummary(
            Long id,
            String name,
            int basePrice,
            int likeCount
    ) {
        public static ProductSummary from(ProductInfo info) {
            return new ProductSummary(info.id(), info.name(), info.basePrice(), info.likeCount());
        }
    }
}
