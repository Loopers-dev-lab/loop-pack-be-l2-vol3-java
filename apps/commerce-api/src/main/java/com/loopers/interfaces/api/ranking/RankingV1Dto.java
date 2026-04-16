package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankInfo;
import com.loopers.application.ranking.RankingResult;

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
}
