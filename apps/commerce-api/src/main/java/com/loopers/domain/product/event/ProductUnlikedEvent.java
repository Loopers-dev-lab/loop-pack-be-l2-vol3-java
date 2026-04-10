package com.loopers.domain.product.event;

import com.loopers.domain.product.ProductLikeModel;
import java.time.ZonedDateTime;

public record ProductUnlikedEvent(
        Long id, Long userId, Long productId, ZonedDateTime createdAt
) {
    public static ProductUnlikedEvent from(ProductLikeModel model) {
        return new ProductUnlikedEvent(model.getId(), model.getUserId(), model.getProductId(), model.getCreatedAt());
    }
}
