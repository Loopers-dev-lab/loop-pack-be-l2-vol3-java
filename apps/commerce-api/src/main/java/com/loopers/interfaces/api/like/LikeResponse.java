package com.loopers.interfaces.api.like;

import java.time.ZonedDateTime;
import java.util.List;

public class LikeResponse {

    /** 상품 좋아요 등록/취소 응답 */
    public record LikeResult(
            boolean liked,
            int likeCount
    ) {}

    /** 내가 좋아요한 상품 요약 */
    public record LikedProductSummary(
            Long productId,
            String name,
            int basePrice,
            String brandName,
            int likeCount,
            ZonedDateTime likedAt
    ) {}

    /** 내가 좋아요한 상품 목록 응답 */
    public record LikedProductListResponse(
            List<LikedProductSummary> products,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    /** 내가 좋아요한 브랜드 요약 */
    public record LikedBrandSummary(
            Long brandId,
            String name,
            String description,
            ZonedDateTime likedAt
    ) {}

    /** 내가 좋아요한 브랜드 목록 응답 */
    public record LikedBrandListResponse(
            List<LikedBrandSummary> brands,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
