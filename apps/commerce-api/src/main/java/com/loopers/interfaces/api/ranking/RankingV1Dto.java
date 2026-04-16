package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.MvRankingPage;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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

    @Getter
    @AllArgsConstructor
    @Builder
    public static class PeriodRankingResponse {
        private RankingPeriod period;
        private LocalDate baseDate;
        private String yearMonth;
        private Integer windowDays;
        private LocalDateTime aggregatedAt;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private List<RankingResponse> content;

        public static PeriodRankingResponse from(MvRankingPage page) {
            return PeriodRankingResponse.builder()
                    .period(page.period())
                    .baseDate(page.baseDate())
                    .yearMonth(page.yearMonth())
                    .windowDays(page.windowDays())
                    .aggregatedAt(page.aggregatedAt())
                    .page(page.page())
                    .size(page.size())
                    .totalElements(page.totalElements())
                    .totalPages(page.totalPages())
                    .content(page.items().stream().map(RankingResponse::from).toList())
                    .build();
        }
    }
}
