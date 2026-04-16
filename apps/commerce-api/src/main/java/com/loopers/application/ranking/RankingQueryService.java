package com.loopers.application.ranking;

import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.ProductRanking;
import com.loopers.domain.ranking.RankingPeriod;

import java.time.LocalDate;
import java.util.Optional;

public interface RankingQueryService {

    /**
     * period 에 따라 일간(Redis) / 주간/월간(MV) 랭킹을 반환한다.
     * Controller 는 이 단일 진입점만 사용한다.
     */
    PageResult<ProductRanking> getRanking(RankingPeriod period, LocalDate baseDate, int page, int size);

    Optional<Long> getProductDailyRank(Long productId, String date);
}
