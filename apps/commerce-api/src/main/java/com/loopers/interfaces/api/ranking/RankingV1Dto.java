package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;
import com.loopers.application.ranking.RankingInfo.RankingItem;
import com.loopers.domain.ranking.RankingPeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class RankingV1Dto {

    // Response

    public record PageResponse(
            RankingPeriod period,
            LocalDate date,
            int page,
            int size,
            long totalCount,
            String experimentGroup,
            List<RankingItemResponse> items
    ) {
        public static PageResponse from(RankingInfo info) {
            List<RankingItemResponse> items = info.items().stream()
                    .map(RankingItemResponse::from)
                    .toList();
            return new PageResponse(
                    info.period(), info.date(), info.page(), info.size(),
                    info.totalCount(), info.experimentGroup(), items
            );
        }
    }

    public record RankingItemResponse(
            int rank,
            double score,
            Long productId,
            String productName,
            BigDecimal price,
            Integer likeCount
    ) {
        public static RankingItemResponse from(RankingItem item) {
            return new RankingItemResponse(
                    item.rank(),
                    item.score(),
                    item.product().id(),
                    item.product().name(),
                    item.product().price(),
                    item.product().likeCount()
            );
        }
    }
}
