package com.loopers.interfaces.api.ranking;

import java.util.List;

public class RankingDto {

    public record RankingInfo(
        long rank,
        double score,
        String date
    ) {}

    public record RankingResponse(
        Long productId,
        String productName,
        String brandName,
        int price,
        long rank,
        double score
    ) {}

    public record PagedRankingResponse(
        List<RankingResponse> data,
        long totalElements,
        int totalPages,
        int page,
        int size
    ) {}
}
