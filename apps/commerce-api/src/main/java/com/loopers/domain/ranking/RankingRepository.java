package com.loopers.domain.ranking;

import java.util.List;

public interface RankingRepository {
    List<RankingEntry> getTopRankings(String key, int offset, int size);
    Long getRank(String key, Long productId);
    Double getScore(String key, Long productId);
}
