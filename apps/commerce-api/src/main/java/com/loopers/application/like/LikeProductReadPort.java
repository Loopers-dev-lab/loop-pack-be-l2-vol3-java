package com.loopers.application.like;

import com.loopers.domain.model.user.UserId;

import java.time.LocalDateTime;
import java.util.List;

public interface LikeProductReadPort {

    List<LikeProductView> findLikedProductsByUserId(UserId userId);

    record LikeProductView(
            Long productId,
            String productName,
            int price,
            Integer salePrice,
            int stockQuantity,
            String brandName,
            LocalDateTime likedAt
    ) {}
}
