package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingCursorResult;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.application.ranking.RankingPageResult;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public class RankingV1Dto {

    public record RankingItemResponse(
            long rank,
            double score,
            Long productDbId,
            String productId,
            String productName,
            BigDecimal price,
            String status
    ) {
        public static RankingItemResponse from(RankingInfo info) {
            return new RankingItemResponse(
                    info.rank(),
                    info.score(),
                    info.productDbId(),
                    info.productId(),
                    info.productName(),
                    info.price(),
                    info.status()
            );
        }
    }

    public record RankingPageResponse(
            List<RankingItemResponse> items,
            long page,
            long size,
            long totalElements,
            ZonedDateTime lastUpdatedAt,
            String periodKey,
            boolean isFallback
    ) {
        public static RankingPageResponse from(RankingPageResult result) {
            List<RankingItemResponse> items = result.items().stream()
                    .map(RankingItemResponse::from)
                    .toList();
            return new RankingPageResponse(items, result.page(), result.size(), result.totalElements(),
                    result.lastUpdatedAt(), result.periodKey(), result.isFallback());
        }
    }

    public record RankingCursorResponse(
            List<RankingItemResponse> items,
            Double nextCursor
    ) {
        public static RankingCursorResponse from(RankingCursorResult result) {
            List<RankingItemResponse> items = result.items().stream()
                    .map(RankingItemResponse::from)
                    .toList();
            return new RankingCursorResponse(items, result.nextCursor());
        }
    }
}
