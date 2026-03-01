package com.loopers.interfaces.api.like.v1;

import com.loopers.application.like.LikedProductResult;

public class LikeDto {

    public record LikedProductResponse(
            Long productId,
            String productName,
            String productThumbnailUrl,
            Long likeCount
    ) {

        public static LikedProductResponse from(LikedProductResult result) {
            return new LikedProductResponse(
                    result.productId(),
                    result.productName(),
                    result.productThumbnailUrl(),
                    result.likeCount()
            );
        }
    }
}
