package com.loopers.interfaces.api.ranking.dto;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.domain.product.ProductStatus;

import java.time.ZonedDateTime;
import java.util.List;

public class RankingV1Dto {

    public record RankingPageResponse(
            String date,
            int page,
            int size,
            long totalElements,
            List<RankingItemResponse> items
    ) {
    }

    public record RankingItemResponse(
            long rank,
            double score,
            Long productId,
            Long brandId,
            String brandName,
            String name,
            int price,
            int discountPrice,
            int shippingFee,
            int likeCount,
            ProductStatus status,
            String displayYn,
            ZonedDateTime createdAt
    ) {

        public static RankingItemResponse from(RankingItemInfo info) {
            ProductInfo p = info.product();
            return new RankingItemResponse(
                    info.rank(),
                    info.score(),
                    p.id(),
                    p.brandId(),
                    p.brandName(),
                    p.name(),
                    p.price(),
                    p.discountPrice(),
                    p.shippingFee(),
                    p.likeCount(),
                    p.status(),
                    p.displayYn(),
                    p.createdAt()
            );
        }
    }
}
