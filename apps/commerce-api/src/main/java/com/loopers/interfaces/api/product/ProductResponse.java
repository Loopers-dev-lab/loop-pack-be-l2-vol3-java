package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductStatus;

import java.util.List;

/** 상품 API 응답 DTO (고객용) - 고객에게 노출하는 정보 최소화 */
public class ProductResponse {

    /** 상품 목록 조회용 요약 정보 */
    public record ProductSummary(
            Long id,
            String name,
            int basePrice,
            String brandName,
            int likeCount,
            String stockStatus
    ) {
        public static ProductSummary from(ProductInfo info, String brandName) {
            return new ProductSummary(
                    info.id(), info.name(), info.basePrice(), brandName,
                    info.likeCount(), toStockStatus(info.status()));
        }

        public static ProductSummary from(ProductInfo info) {
            return new ProductSummary(
                    info.id(), info.name(), info.basePrice(), null,
                    info.likeCount(), toStockStatus(info.status()));
        }
    }

    /** 상품 상세 조회용 정보 */
    public record ProductDetail(
            Long id,
            String name,
            String description,
            int basePrice,
            String brandName,
            int likeCount,
            String stockStatus,
            Integer rank
    ) {
        public static ProductDetail from(ProductInfo info, String brandName, Integer rank) {
            return new ProductDetail(
                    info.id(), info.name(), info.description(), info.basePrice(),
                    brandName, info.likeCount(), toStockStatus(info.status()), rank);
        }
    }

    /** 상품 목록 + Cursor 페이지네이션 메타 정보 */
    public record ProductCursorListResponse(
            List<ProductSummary> products,
            PagingInfo paging
    ) {}

    public record PagingInfo(
            boolean hasNext,
            String nextCursor,
            int size
    ) {}

    /** 상품 상태 → 고객용 재고 상태 문자열 변환 */
    private static String toStockStatus(ProductStatus status) {
        return switch (status) {
            case ACTIVE -> "IN_STOCK";
            case SOLDOUT -> "SOLD_OUT";
            default -> "UNAVAILABLE";
        };
    }
}
