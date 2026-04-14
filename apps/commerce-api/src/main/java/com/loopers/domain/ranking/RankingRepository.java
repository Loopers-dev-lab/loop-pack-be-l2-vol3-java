package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingRepository {

    List<Long> findProductIdsByRank(LocalDate date, long offset, long limit);

    long countByDate(LocalDate date);

    Optional<Long> findRankByProductId(LocalDate date, Long productId);
}
