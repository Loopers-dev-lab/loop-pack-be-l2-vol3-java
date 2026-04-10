package com.loopers.interfaces.api.ranking.dto;

import com.loopers.application.ranking.dto.FindRankingItemResDto;
import com.loopers.application.ranking.dto.FindRankingListResDto;

import java.util.List;

public record FindRankingListApiResDto(
        List<RankingItemApiResDto> rankings,
        long totalCount,
        int page,
        int size
) {
    public record RankingItemApiResDto(
            long rank,
            double score,
            Long productId,
            String productName,
            int price
    ) {
        public static RankingItemApiResDto from(FindRankingItemResDto dto) {
            return new RankingItemApiResDto(
                    dto.rank(),
                    dto.score(),
                    dto.productId(),
                    dto.productName(),
                    dto.price()
            );
        }
    }

    public static FindRankingListApiResDto from(FindRankingListResDto dto) {
        List<RankingItemApiResDto> items = dto.rankings().stream()
                .map(RankingItemApiResDto::from)
                .toList();
        return new FindRankingListApiResDto(items, dto.totalCount(), dto.page(), dto.size());
    }
}
