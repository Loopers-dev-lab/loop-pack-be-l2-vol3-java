package com.loopers.interfaces.ranking.dto;

import com.loopers.application.ranking.dto.RankingResult;
import java.time.LocalDate;
import java.util.List;

public class RankingV1Dto {

    public record ListResponse(
            LocalDate date,
            int page,
            int size,
            long totalElements,
            List<RankingItem> items) {

        public static ListResponse from(RankingResult.RankingPage rankingPage) {
            return new ListResponse(
                    rankingPage.date(),
                    rankingPage.page(),
                    rankingPage.size(),
                    rankingPage.totalElements(),
                    rankingPage.items().stream()
                            .map(RankingItem::from)
                            .toList());
        }
    }

    public record RankingItem(
            long rank,
            Long productId,
            String productName,
            String brandName,
            int price,
            String thumbnailUrl,
            double score) {

        public static RankingItem from(RankingResult.RankingEntry entry) {
            return new RankingItem(
                    entry.rank(),
                    entry.productId(),
                    entry.productName(),
                    entry.brandName(),
                    entry.price(),
                    entry.thumbnailUrl(),
                    entry.score());
        }
    }
}
