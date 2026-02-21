package com.loopers.application.like;

import com.loopers.domain.product.Product;

public record LikedProductResult(
        Long productId,
        String productName,
        String productThumbnailUrl,
        Long likeCount
) {

    public static LikedProductResult from(Product product, Long likeCount) {
        return new LikedProductResult(
                product.getId(),
                product.getName().getValue(),
                product.getThumbnailUrl().getValue(),
                likeCount
        );

    }
}
