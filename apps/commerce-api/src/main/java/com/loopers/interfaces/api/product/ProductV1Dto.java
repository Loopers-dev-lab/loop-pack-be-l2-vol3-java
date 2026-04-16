package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 상품 API V1 요청/응답 DTO 모음.
 *
 * <p>상품 관련 REST API의 HTTP 응답 데이터 구조를 정의한다.</p>
 */
public class ProductV1Dto {

    /**
     * 상품 목록 조회 응답 DTO.
     *
     * <p>상품 ID, 브랜드 ID, 상품명, 가격, 이미지 URL, 가용 재고를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class ProductResponse {
        private Long productId;
        private Long brandId;
        private String brandName;
        private String productName;
        private BigDecimal price;
        private String imageUrl;
        private int availableStock;
        private long likeCount;

        /**
         * {@link ProductInfo}를 상품 목록 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param info 변환할 상품 도메인 Info 객체
         * @return 변환된 ProductResponse
         */
        public static ProductResponse from(ProductInfo info) {
            return ProductResponse.builder()
                    .productId(info.getProductId())
                    .brandId(info.getBrandId())
                    .brandName(info.getBrandName())
                    .productName(info.getProductName())
                    .price(info.getPrice())
                    .imageUrl(info.getImageUrl())
                    .availableStock(info.getAvailableStock())
                    .likeCount(info.getLikeCount())
                    .build();
        }
    }

    /**
     * 상품 상세 조회 응답 DTO.
     *
     * <p>상품의 전체 상세 정보(설명, 카테고리, 색상, 사이즈, 옵션 등)를 포함한다.</p>
     */
    @Getter
    @AllArgsConstructor
    @Builder
    public static class ProductDetailResponse {
        private Long productId;
        private Long brandId;
        private String brandName;
        private String productName;
        private String description;
        private BigDecimal price;
        private String category;
        private String color;
        private String size;
        private String option;
        private String imageUrl;
        private int availableStock;
        private long likeCount;
        private Long rank;

        /**
         * {@link ProductInfo}를 상품 상세 응답 DTO로 변환하는 팩토리 메서드.
         *
         * @param info 변환할 상품 도메인 Info 객체
         * @return 변환된 ProductDetailResponse
         */
        public static ProductDetailResponse from(ProductInfo info) {
            return ProductDetailResponse.builder()
                    .productId(info.getProductId())
                    .brandId(info.getBrandId())
                    .brandName(info.getBrandName())
                    .productName(info.getProductName())
                    .description(info.getDescription())
                    .price(info.getPrice())
                    .category(info.getCategory())
                    .color(info.getColor())
                    .size(info.getSize())
                    .option(info.getOption())
                    .imageUrl(info.getImageUrl())
                    .availableStock(info.getAvailableStock())
                    .likeCount(info.getLikeCount())
                    .rank(info.getRank())
                    .build();
        }
    }
}
