package com.loopers.application.like;

import com.loopers.domain.model.user.UserId;

import java.time.LocalDateTime;
import java.util.List;

public interface LikeQueryUseCase {

    List<LikeInfo> getMyLikes(UserId userId, String sort, Boolean saleYn, String status);

    record LikeInfo(
            Long productId,
            String productName,
            int price,
            Integer salePrice,
            boolean onSale,
            int discountRate,
            String brandName,
            boolean soldOut,
            LocalDateTime likedAt
    ) {}
}
