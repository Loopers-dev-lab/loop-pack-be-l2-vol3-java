package com.loopers.application.ranking;

import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.ProductRanking;

import java.util.Optional;

public interface RankingQueryService {

    PageResult<ProductRanking> getDailyRanking(String date, int page, int size);

    Optional<Long> getProductDailyRank(Long productId, String date);
}
