package com.loopers.application.product.dto;

import com.loopers.domain.product.ProductLikeModel;
import java.time.ZonedDateTime;
import java.util.List;

public record ProductLikeResult(Long id, Long userId, Long productId, ZonedDateTime createdAt) {
    public static ProductLikeResult from(ProductLikeModel model) {
        return new ProductLikeResult(model.getId(), model.getUserId(), model.getProductId(), model.getCreatedAt());
    }
    public static List<ProductLikeResult> from(List<ProductLikeModel> models) {
        return models.stream()
                .map(model ->
                        new ProductLikeResult(model.getId(), model.getUserId(), model.getProductId(), model.getCreatedAt()))
                .toList();
    }
}
