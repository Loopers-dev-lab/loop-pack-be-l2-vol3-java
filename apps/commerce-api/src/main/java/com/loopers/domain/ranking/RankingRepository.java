package com.loopers.domain.ranking;

import java.util.List;

public interface RankingRepository {

    List<RankingEntry> getTopRankings(String key, long start, long end);

    Long getRank(String key, Long productId);

    Double getScore(String key, Long productId);
}
