package com.loopers.domain.ranking;

import java.util.List;
import java.util.Optional;

public interface RankingRepository {

    List<ProductRanking> getTopN(String key, long start, long stop);

    Optional<Long> getRank(String key, Long productId);

    long getTotalCount(String key);
}
