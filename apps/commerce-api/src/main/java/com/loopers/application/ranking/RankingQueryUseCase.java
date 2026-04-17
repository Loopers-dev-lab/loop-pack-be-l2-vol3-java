package com.loopers.application.ranking;

import com.loopers.domain.model.common.PageResult;
import com.loopers.domain.model.ranking.RankingPeriod;

import java.time.LocalDate;

public interface RankingQueryUseCase {

    PageResult<RankingItemInfo> getRankings(LocalDate date, int page, int size);

    PageResult<RankingItemInfo> getRankings(LocalDate date, int page, int size, RankingPeriod period);

    Long getProductRank(LocalDate date, Long productId);

    record RankingItemInfo(
            long rank,
            double score,
            Long productId,
            Long brandId,
            String brandName,
            String productName,
            int price,
            Integer salePrice,
            boolean onSale,
            int likeCount
    ) {}
}
