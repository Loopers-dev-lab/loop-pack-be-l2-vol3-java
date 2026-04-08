package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

public class RankingV1Dto {

    @Getter
    @AllArgsConstructor
    @Builder
    public static class RankingResponse {
        private long rank;
        private Long productId;
        private String productName;
        private String brandName;
        private BigDecimal price;
        private String imageUrl;
        private double score;

        public static RankingResponse from(RankingInfo info) {
            return RankingResponse.builder()
                    .rank(info.getRank())
                    .productId(info.getProductId())
                    .productName(info.getProductName())
                    .brandName(info.getBrandName())
                    .price(info.getPrice())
                    .imageUrl(info.getImageUrl())
                    .score(info.getScore())
                    .build();
        }
    }
}
