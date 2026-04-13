package com.loopers.domain.ranking.repository;

import java.util.List;

public interface RankingRepository {
    List<RankingEntry> getTopRankings(String date, int offset, int size);
    long getTotalCount(String date);
    RankingEntry getProductRanking(String date, Long productId);

    record RankingEntry(Long productId, double score, long rank) {
    }
}
