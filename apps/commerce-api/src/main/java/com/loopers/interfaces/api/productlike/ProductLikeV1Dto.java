package com.loopers.interfaces.api.productlike;

import com.loopers.application.productlike.ProductLikeInfo;

import java.time.ZonedDateTime;

public class ProductLikeV1Dto {

    public record Response(
            Long id,
            Long userId,
            Long productId,
            ZonedDateTime createdAt
    ) {
        public static Response from(ProductLikeInfo info) {
            return new Response(
                    info.id(),
                    info.userId(),
                    info.productId(),
                    info.createdAt()
            );
        }
    }
}
