package com.loopers.interfaces.api.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;
import java.util.List;

public class LikeV1Dto {

    public record LikeResponse(
        Long likeId,
        Long productId,
        String productName,
        String brandName,
        int price,
        int likeCount,
        ZonedDateTime likedAt
    ) {
        public static LikeResponse from(Like like, Product product, Brand brand) {
            return new LikeResponse(
                like.getId(), product.getId(), product.getName(),
                brand.getName(), product.getPrice().amount(), product.getLikeCount(), like.getCreatedAt()
            );
        }
    }

    public record LikeListResponse(List<LikeResponse> likes) {
        public static LikeListResponse from(List<LikeResponse> likes) {
            return new LikeListResponse(likes);
        }
    }
}
