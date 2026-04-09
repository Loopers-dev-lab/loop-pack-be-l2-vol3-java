package com.loopers.interfaces.api.product;

import java.util.List;

public class ProductV1Dto {

    // 상품 등록 요청
    public record RegisterRequest(
            Long brandId,
            String name,
            String description,
            String imageUrl,
            int price,
            int stockQuantity
    ) {}

    // 상품 등록 응답 (기본 정보)
    public record ProductResponse(
            Long id,
            Long brandId,
            String name,
            String description,
            String imageUrl,
            int price,
            int stockQuantity
    ) {}

    // 상품 상세 응답 (브랜드명 + 좋아요 수 포함)
    public record ProductDetailResponse(
            Long id,
            String name,
            String description,
            String imageUrl,
            int price,
            int stockQuantity,
            String brandName,
            long likeCount,
            Long rank
    ) {}

    // 상품 목록 응답
    public record ProductListResponse(
            List<ProductDetailResponse> products
    ) {}
}
