package com.loopers.domain.product.event;

import com.loopers.domain.product.ProductLikeModel;
import java.time.ZonedDateTime;

public record ProductLikedEvent(
        Long id, Long userId, Long productId, ZonedDateTime createdAt
) {
    public static ProductLikedEvent from(ProductLikeModel model){
        return new ProductLikedEvent(model.getId(),model.getUserId(),model.getProductId(),model.getCreatedAt());
    }
}
