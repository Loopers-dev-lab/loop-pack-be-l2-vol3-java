package com.loopers.application.like;

import com.loopers.domain.common.Money;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LikeInfo {
    private final Long likeId;
    private final Long productId;
    private final String productName;
    private final Money basePrice;

    public static LikeInfo of(Like like, Product product) {
        return LikeInfo.builder()
                .likeId(like.getId())
                .productId(product.getId())
                .productName(product.getName())
                .basePrice(product.getBasePrice())
                .build();
    }
}
