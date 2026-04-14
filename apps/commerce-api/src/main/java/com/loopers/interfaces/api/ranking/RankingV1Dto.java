package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankedProductInfo;
import com.loopers.application.ranking.HourlyRankingPageInfo;
import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.interfaces.api.product.ProductV1Dto;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class RankingV1Dto {
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter BASIC_HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");

    public record RankingPageResponse(
        String date,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<RankedProductResponse> content
    ) {
        public static RankingPageResponse from(RankingPageInfo pageInfo) {
            return new RankingPageResponse(
                BASIC_DATE.format(pageInfo.date()),
                pageInfo.page(),
                pageInfo.size(),
                pageInfo.totalElements(),
                pageInfo.totalPages(),
                pageInfo.content().stream().map(RankedProductResponse::from).toList()
            );
        }
    }

    public record HourlyRankingPageResponse(
        String hour,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<RankedProductResponse> content
    ) {
        public static HourlyRankingPageResponse from(HourlyRankingPageInfo pageInfo) {
            return new HourlyRankingPageResponse(
                BASIC_HOUR.format(pageInfo.hour()),
                pageInfo.page(),
                pageInfo.size(),
                pageInfo.totalElements(),
                pageInfo.totalPages(),
                pageInfo.content().stream().map(RankedProductResponse::from).toList()
            );
        }
    }

    public record RankedProductResponse(
        long rank,
        double score,
        ProductV1Dto.ProductResponse product
    ) {
        public static RankedProductResponse from(RankedProductInfo info) {
            return new RankedProductResponse(
                info.rank(),
                info.score(),
                ProductV1Dto.ProductResponse.from(info.product())
            );
        }
    }
}
