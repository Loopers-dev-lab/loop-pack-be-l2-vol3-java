package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.PeriodRankingResult;
import com.loopers.application.ranking.RankInfo;
import com.loopers.application.ranking.RankingPeriod;
import com.loopers.application.ranking.RankingResult;

import java.math.BigDecimal;
import java.util.List;

public class RankingV1Dto {

    public record RankingResponse(
        int rank,
        Long productId,
        String productName,
        Long productPrice,
        String productImageUrl,
        Double score
    ) {
        public static RankingResponse from(RankingResult result) {
            return new RankingResponse(
                result.rank(),
                result.productId(),
                result.productName(),
                result.productPrice(),
                result.productImageUrl(),
                result.score()
            );
        }
    }

    public record RankingPageResponse(
        List<RankingResponse> rankings,
        String date,
        int page,
        int size,
        long totalCount,
        int totalPages
    ) {
        public static RankingPageResponse of(List<RankingResponse> rankings, String date, int page, int size, long totalCount) {
            int totalPages = (int) Math.ceil((double) totalCount / size);
            return new RankingPageResponse(rankings, date, page, size, totalCount, totalPages);
        }
    }

    public record RankInfoResponse(
        Integer rank,
        Double score
    ) {
        public static RankInfoResponse from(RankInfo info) {
            if (info == null) {
                return new RankInfoResponse(null, null);
            }
            return new RankInfoResponse(info.rank(), info.score());
        }
    }

    public record HourlyRankingPageResponse(
        List<RankingResponse> rankings,
        String hour,
        int page,
        int size,
        long totalCount,
        int totalPages
    ) {
        public static HourlyRankingPageResponse of(List<RankingResponse> rankings, String hour, int page, int size, long totalCount) {
            int totalPages = (int) Math.ceil((double) totalCount / size);
            return new HourlyRankingPageResponse(rankings, hour, page, size, totalCount, totalPages);
        }
    }

    /**
     * 기간별 랭킹 단일 항목 응답.
     * 일간/주간/월간 랭킹을 통합 표현합니다.
     */
    public record PeriodRankingResponse(
        int rank,
        Long productId,
        String productName,
        Long productPrice,
        String productImageUrl,
        BigDecimal score,
        Long viewCount,
        Long likeCount,
        Long orderCount,
        RankingPeriod period,
        String periodStart,
        String periodEnd
    ) {
        private static final java.time.format.DateTimeFormatter DATE_FORMATTER =
            java.time.format.DateTimeFormatter.BASIC_ISO_DATE;

        public static PeriodRankingResponse from(PeriodRankingResult result) {
            return new PeriodRankingResponse(
                result.rank(),
                result.productId(),
                result.productName(),
                result.productPrice(),
                result.productImageUrl(),
                result.score(),
                result.viewCount(),
                result.likeCount(),
                result.orderCount(),
                result.period(),
                result.periodStart() != null ? result.periodStart().format(DATE_FORMATTER) : null,
                result.periodEnd() != null ? result.periodEnd().format(DATE_FORMATTER) : null
            );
        }
    }

    /**
     * 기간별 랭킹 페이지 응답.
     */
    public record PeriodRankingPageResponse(
        List<PeriodRankingResponse> rankings,
        String date,
        RankingPeriod period,
        String periodStart,
        String periodEnd,
        int page,
        int size,
        long totalCount,
        int totalPages
    ) {
        public static PeriodRankingPageResponse of(
            List<PeriodRankingResponse> rankings,
            String date,
            RankingPeriod period,
            String periodStart,
            String periodEnd,
            int page,
            int size,
            long totalCount
        ) {
            int totalPages = (int) Math.ceil((double) totalCount / size);
            return new PeriodRankingPageResponse(
                rankings, date, period, periodStart, periodEnd, page, size, totalCount, totalPages
            );
        }
    }
}
