package com.loopers.application.ranking;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class RankingInfo {
    private final long rank;
    private final Long productId;
    private final String productName;
    private final String brandName;
    private final BigDecimal price;
    private final String imageUrl;
    private final double score;

    public static RankingInfo of(long rank, Long productId, String productName,
                                  String brandName, BigDecimal price,
                                  String imageUrl, double score) {
        return RankingInfo.builder()
                .rank(rank)
                .productId(productId)
                .productName(productName)
                .brandName(brandName)
                .price(price)
                .imageUrl(imageUrl)
                .score(score)
                .build();
    }
}
