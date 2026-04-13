package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

import java.math.BigDecimal;
import java.util.List;

public class RankingDto {

    public record RankingResponse(
            int rank,
            double score,
            Long productId,
            String productName,
            BigDecimal basePrice
    ) {
        public static RankingResponse from(RankingInfo info) {
            return new RankingResponse(
                    info.rank(),
                    info.score(),
                    info.productId(),
                    info.productName(),
                    info.basePrice().getAmount()
            );
        }
    }

    public record RankingListResponse(
            List<RankingResponse> rankings,
            int page,
            int size
    ) {
    }
}
