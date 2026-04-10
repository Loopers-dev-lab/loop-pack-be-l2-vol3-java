package com.loopers.domain.ranking;

import java.util.List;

public interface RankingRepository {

    List<RankedProduct> findTopN(String date, int offset, int size);

    Long findRank(String date, Long productId);

    long getTotalSize(String date);
}
